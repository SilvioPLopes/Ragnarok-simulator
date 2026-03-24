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
| F — Resilience4j | Add retry + circuit breaker to rAthena download | `pom.xml`, `RathenaDownloadService` (new), `RathenaImporter`, `application.properties` |

### Wave 2 — 1 agent (after Wave 1 stable)

| Feature | Description | Files touched |
|---|---|---|
| D — Spring Events | Decouple `BattleService` via domain events | `BattleService`, `domain/event/` (new), `application/service/BattleEventHandler` (new), `PlayerService` |

---

## Feature A — REST API + Swagger

### Dependency

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

Compatible with Spring Boot 3.4.x. `spring-boot-starter-web` is already present in `pom.xml`.

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
│       ├── AttackRequestDTO      { playerId, monsterId }
│       ├── TravelRequestDTO      { destination }
│       └── UseSkillRequestDTO    { monsterId (nullable) }
└── GlobalExceptionHandler        (@RestControllerAdvice)
```

### Endpoints

POST endpoints use **request body DTOs**, not query parameters. Path variables carry resource identifiers.

**PlayerController**
- `GET /api/players` → list of `PlayerResponseDTO`
- `GET /api/players/{id}` → `PlayerResponseDTO`
- `POST /api/players` → create player

**BattleController**
- `POST /api/battle/attack` body `{ playerId, monsterId }` → `BattleResponseDTO { playerDamage, monsterDamage, monsterHpAfter, monsterDead, loot, xpGained, levelUp, message }`

**SkillController**
- `GET /api/players/{id}/skills` → list of `SkillRowResponseDTO`
- `POST /api/players/{id}/skills/{skillName}/learn` → result message
- `POST /api/players/{id}/skills/{skillName}/use` body `{ monsterId }` (nullable) → `SkillUseResponseDTO { damage, buffApplied, healAmount, message }`

**ItemController**
- `GET /api/players/{id}/inventory` → list of inventory items
- `POST /api/players/{id}/inventory/{itemId}/use` → result message

**MapController**
- `GET /api/players/{id}/map` → current map and available portals
- `GET /api/maps/{mapId}/portals` → portal destinations
- `POST /api/players/{id}/map/walk` → random encounter result
- `POST /api/players/{id}/map/travel` body `{ destination }` → travel result

### Error handling

`GlobalExceptionHandler` maps:
- `GameException` subclasses → 400 or 404 depending on type
- `EntityNotFoundException` → 404
- Unexpected → 500

All error responses: `{ "error": "message" }`

### Testing

Each controller gets a `@WebMvcTest` test class using `MockMvc`. Tests cover: happy path, invalid IDs (404), game rule violations (400). Controller tests mock the service layer — they do not extend `AbstractIntegrationTest`.

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

Both are managed by `spring-boot-dependencies` BOM — no explicit version needed.

### Design

New abstract base class `AbstractIntegrationTest` shared by all integration test classes:

```java
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @MockBean
    RagnarokTerminalRunner ragnarokTerminalRunner;  // suppresses interactive terminal during tests

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16");   // withReuse(true) intentionally omitted (see note)

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**Note on `withReuse(true)`:** Container reuse requires `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` on each developer machine. Since this is a public portfolio project where contributors may not have this configured, `withReuse` is omitted. The `static` container is already shared for the full test suite in one JVM run, which is sufficient.

**Note on `@MockBean RagnarokTerminalRunner`:** The existing integration tests already declare this mock individually. Moving it to `AbstractIntegrationTest` centralizes it — subclasses must not re-declare it.

All `*IntegrationTest` classes extend `AbstractIntegrationTest` and remove their individual `@MockBean RagnarokTerminalRunner` declarations.

`application-test.properties` removes the hardcoded datasource URL, username, and password (replaced by `@DynamicPropertySource`).

### Result

`./mvnw test` works on any machine with Docker installed, without local PostgreSQL, without environment variables.

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

Managed by Spring Boot BOM — no version needed.

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
- `WeaponSizeService.getModifier(WeaponType, String)` → `@Cacheable("weaponSizeModifiers")` — default key uses both parameters; no explicit `key` needed since `WeaponType` is an enum with natural equality
- `SkillService.listarSkillsDoPlayer(Long playerId)` → `@Cacheable(value="playerSkills", key="#playerId")`
- `SkillService.aprenderSkill(Long playerId, String skillName)` → `@CacheEvict(value="playerSkills", key="#playerId")`
- `SkillCombatService` buff effect lookup → `@Cacheable("skillBuffEffects")`

**Deliberately uncached:** `listarSkillsUsaveisForaDeCombate()` — this method is already a filtered subset of `listarSkillsDoPlayer()` results and is called infrequently (only in the out-of-combat skill menu). Caching it separately would require a second eviction point on `aprenderSkill()` with a different cache key; the cost is not justified.

### Testing

`CacheVerificationTest` verifies that a second call to `WeaponSizeService.getModifier()` does not trigger an additional database query (using `@SpyBean` on the repository to count invocations).

---

## Feature F — Resilience4j

### Problem

`RathenaImporter` downloads data from GitHub on startup with no retry, timeout, or fallback. Network failure silently skips the import.

### Root cause for design

Resilience4j AOP proxies only intercept calls via the Spring proxy (calls from outside the bean). `RathenaImporter.run()` calls its own private download helper directly — those internal calls bypass the proxy and cannot be decorated. The download logic must be extracted to a separate Spring bean so that Resilience4j can intercept it.

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

New `@Service RathenaDownloadService` encapsulates the HTTP download:

```java
@Service
public class RathenaDownloadService {

    @Retry(name = "rathena", fallbackMethod = "downloadFalhou")
    @CircuitBreaker(name = "rathena")
    public String downloadYaml(String url) {
        // RestTemplate.getForObject(url, String.class)
    }

    private String downloadFalhou(String url, Exception e) {
        log.warn("rAthena unavailable after retries ({}). Starting with existing data.", url);
        return null;  // RathenaImporter checks for null and skips import gracefully
    }
}
```

`RathenaImporter` injects `RathenaDownloadService` and delegates all HTTP calls to it. `RathenaImporter.run()` itself is not annotated.

**Production files modified:** `RathenaImporter.java` (inject new service, replace HTTP calls), `RathenaDownloadService.java` (new).

---

## Feature D — Spring Events

### Problem

`BattleService` directly orchestrates loot, XP distribution, level up, and player death in a single method. Adding any new consequence requires editing `BattleService`.

### Design

**New domain events** (`com.ragnarok.domain.event`, immutable records — no Spring dependencies):

```java
record MonsterKilledEvent(Long playerId, Long monsterId, List<ItemDropInfo> drops, int xpBase, int xpJob) {}
record PlayerLeveledUpEvent(Long playerId, int newBaseLevel, int newJobLevel) {}
record PlayerDiedEvent(Long playerId) {}
```

**`BattleService` after refactor:** publishes events instead of calling services directly:

```java
eventPublisher.publishEvent(new MonsterKilledEvent(...));
eventPublisher.publishEvent(new PlayerDiedEvent(...));
```

**New `BattleEventHandler`** (`com.ragnarok.application.service`) — single class holds all `@EventListener` methods, preserving the hexagonal boundary:

```java
@Component
public class BattleEventHandler {

    @EventListener
    public void onMonsterKilled(MonsterKilledEvent event) {
        // delegate to LevelingService (XP + level up)
        // persist loot drops
        // publish PlayerLeveledUpEvent if level up occurred
    }

    @EventListener
    public void onPlayerDied(PlayerDiedEvent event) {
        // playerService.ressuscitarJogador(event.playerId())
        // reset map to "prontera" via PlayerRepository
    }
}
```

`LevelingService` **stays pure domain** — no `@EventListener` or any Spring annotation is added to it. `BattleEventHandler` calls `levelingService.processarXp(...)` as a plain method call.

**`PlayerService.onPlayerDied()` listener is NOT added to `PlayerService`** — `BattleEventHandler` handles death entirely, keeping `PlayerService` a simple orchestration service without event coupling.

**Public API of `BattleService` does not change.** Terminal and REST API continue calling `battleService.realizarAtaque()` without modification.

**`RagnarokTerminalRunner.handlePlayerDeath()`:** the resurrection and map-reset logic is removed from the terminal runner since `BattleEventHandler` now handles it on `PlayerDiedEvent`. The terminal runner only sets `inBattle = false`, clears `currentMonster`, and prints the death message.

### Why Wave 2

`BattleService` is the most central service. Refactoring it after the REST API is stable and tested reduces regression risk.

---

## Non-goals

- Game mechanics (AGI/FLEE/ASPD, HIT/DEX, NPC shop, etc.) — out of scope for this release
- Frontend web — Swagger serves as the foundation for a future frontend, not implemented now
- Deploy/hosting — separate concern after all features are implemented
