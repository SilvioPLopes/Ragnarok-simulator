# Design: Portfolio Improvements — ragnarok-core

**Date:** 2026-03-23
**Status:** Approved
**Goal:** Elevate the project to portfolio-ready quality for public release, with a REST API foundation for a potential future web frontend.

---

## Context

The project is a Ragnarok Online emulation engine built on Hexagonal Architecture with 212 tests and 85%+ coverage. The terminal game loop is fully functional, including skills in combat, item usage, class change, and stat distribution. The objective is to add technical depth visible to recruiters and remove friction for anyone evaluating the project publicly.

---

## Execution Plan

Work is organized in two dependency waves. All features in Wave 1 touch disjoint files and can be developed by parallel agents with zero merge conflict risk.

### Wave 1 — 4 parallel agents

| Feature | Description | Files touched |
|---|---|---|
| A — REST API + Swagger | Expose the full game loop as HTTP endpoints | `api/` (new), `pom.xml` |
| B — Testcontainers | Replace hardcoded PostgreSQL config in integration tests | `pom.xml`, `*IntegrationTest.java`, `application-test.properties` |
| C — Spring Cache | Cache static game data with Caffeine | `pom.xml`, `WeaponSizeService`, `SkillService`, `SkillCombatService`, `CacheConfig` (new) |
| F — Resilience4j | Add retry + circuit breaker to `RathenaImporter` | `pom.xml`, `RathenaImporter`, `application.properties` |

### Wave 2 — 1 agent (after Wave 1 stable)

| Feature | Description | Files touched |
|---|---|---|
| D — Spring Events | Decouple `BattleService` via domain events | `BattleService`, `domain/event/` (new), `LevelingService`, `PlayerService` |

---

## Feature A — REST API + Swagger

### Dependency

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.x</version>
</dependency>
```

### New package structure

```
com.ragnarok.api
├── controller
│   ├── PlayerController
│   ├── BattleController
│   ├── SkillController
│   ├── ItemController
│   └── MapController
├── dto
│   ├── response
│   │   ├── PlayerResponseDTO
│   │   ├── BattleResponseDTO
│   │   ├── SkillUseResponseDTO
│   │   ├── SkillRowResponseDTO
│   │   └── InventoryResponseDTO
│   └── request
│       └── (where needed)
└── GlobalExceptionHandler   (@RestControllerAdvice)
```

### Endpoints

**PlayerController** `GET /api/players`, `GET /api/players/{id}`, `POST /api/players`

**BattleController**
- `POST /api/battle/attack?playerId=&monsterId=` → `BattleResponseDTO { playerDamage, monsterDamage, monsterHpAfter, monsterDead, loot, xpGained, levelUp, message }`

**SkillController**
- `GET /api/players/{id}/skills` → list of `SkillRowResponseDTO`
- `POST /api/players/{id}/skills/{skillName}/learn` → result message
- `POST /api/players/{id}/skills/{skillName}/use?monsterId=` → `SkillUseResponseDTO { damage, buffApplied, healAmount, message }`

**ItemController**
- `GET /api/players/{id}/inventory` → list of inventory items
- `POST /api/players/{id}/inventory/{itemId}/use` → result message

**MapController**
- `GET /api/players/{id}/map` → current map and available portals
- `GET /api/maps/{mapId}/portals` → portal destinations
- `POST /api/players/{id}/map/walk` → random encounter result
- `POST /api/players/{id}/map/travel?destination=` → travel result

### Error handling

`GlobalExceptionHandler` maps:
- `GameException` subclasses → 400 or 404 depending on type
- `EntityNotFoundException` → 404
- Unexpected → 500

All error responses: `{ "error": "message" }`

### Principles

- Controllers are pure delegators — zero business logic
- All intelligence remains in existing services
- `@Operation` and `@Tag` Swagger annotations on all endpoints
- Swagger UI accessible at `/swagger-ui.html`

---

## Feature B — Testcontainers

### Problem

`application-test.properties` hardcodes `jdbc:postgresql://localhost:5432/ragnarok_test`. Running `./mvnw test` without a local PostgreSQL fails.

### Dependencies

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Design

New abstract base class `AbstractIntegrationTest` shared by all integration test classes:

```java
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16").withReuse(true);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

- `static` container is shared across all test suites in the same JVM run — one startup, not one per class
- `withReuse(true)` avoids container teardown between test classes
- All `*IntegrationTest` classes extend `AbstractIntegrationTest`
- `application-test.properties` removes hardcoded datasource URL

### Result

`./mvnw test` works on any machine with Docker, without local PostgreSQL, without environment variables.

---

## Feature C — Spring Cache

### Problem

Each battle turn and skill listing triggers queries against `weapon_size_modifiers`, `skill_tree`, and `skill_buff_effects` — tables that never change at runtime.

### Dependency

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

### Design

New `CacheConfig.java` defines named caches with Caffeine:

| Cache name | TTL | Rationale |
|---|---|---|
| `weaponSizeModifiers` | infinite | Static table, never changes at runtime |
| `skillTree` | infinite | Static table |
| `skillBuffEffects` | infinite | Static table |
| `playerSkills` | 5 minutes | Changes when player learns a skill |

`@EnableCaching` added to `RagnarokCoreApplication`.

**Annotations on existing services (no logic changes):**
- `WeaponSizeService.getModifier()` → `@Cacheable("weaponSizeModifiers")`
- `SkillService.listarSkillsDoPlayer()` → `@Cacheable(value="playerSkills", key="#playerId")`
- `SkillService.aprenderSkill()` → `@CacheEvict(value="playerSkills", key="#playerId")`
- `SkillCombatService` buff effect lookup → `@Cacheable("skillBuffEffects")`

---

## Feature F — Resilience4j

### Problem

`RathenaImporter` downloads data from GitHub on startup with no retry, timeout, or fallback. Network failure silently skips the import.

### Dependency

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

### Configuration (`application.properties`)

```properties
resilience4j.retry.instances.rathena.max-attempts=3
resilience4j.retry.instances.rathena.wait-duration=2s
resilience4j.retry.instances.rathena.enable-exponential-backoff=true
resilience4j.retry.instances.rathena.exponential-backoff-multiplier=2

resilience4j.circuitbreaker.instances.rathena.sliding-window-size=3
resilience4j.circuitbreaker.instances.rathena.failure-rate-threshold=100
resilience4j.circuitbreaker.instances.rathena.wait-duration-in-open-state=30s
```

### Design

In `RathenaImporter`, the download method is annotated:

```java
@Retry(name = "rathena", fallbackMethod = "importacaoFalhou")
@CircuitBreaker(name = "rathena")
public void run(String... args) { ... }

private void importacaoFalhou(Exception e) {
    log.warn("rAthena unavailable after retries. Starting with existing data. Cause: {}", e.getMessage());
}
```

Only `RathenaImporter.java` is modified in production code.

---

## Feature D — Spring Events

### Problem

`BattleService` directly orchestrates loot, XP distribution, level up, and player death in a single method. Adding any new consequence requires editing `BattleService`.

### Design

**New domain events** (`com.ragnarok.domain.event`, immutable records):

```java
record MonsterKilledEvent(Long playerId, Long monsterId, List<ItemDropInfo> drops, int xpBase, int xpJob) {}
record PlayerLeveledUpEvent(Long playerId, int newBaseLevel, int newJobLevel) {}
record PlayerDiedEvent(Long playerId) {}
```

**`BattleService` after refactor:** publishes events instead of calling services directly:

```java
eventPublisher.publishEvent(new MonsterKilledEvent(...));
```

**Listeners via `@EventListener`** added to existing services:
- `LevelingService.onMonsterKilled(MonsterKilledEvent)` — processes XP, checks level up, publishes `PlayerLeveledUpEvent`
- Loot persistence listener — persists drops on `MonsterKilledEvent`
- `PlayerService.onPlayerDied(PlayerDiedEvent)` — resurrects player

**Public API of `BattleService` does not change.** Terminal and REST API continue calling `battleService.realizarAtaque()` without modification.

### Why Wave 2

`BattleService` is the most central service. Refactoring it after the REST API is stable and tested reduces regression risk.

---

## Non-goals

- Game mechanics (AGI/FLEE/ASPD, HIT/DEX, NPC shop, etc.) — out of scope for this release
- Frontend web — Swagger serves as the foundation for a future frontend, not implemented now
- Deploy/hosting — separate concern after all features are implemented
