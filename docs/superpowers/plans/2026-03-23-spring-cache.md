# Spring Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache static game data (weapon size modifiers, skill buff effects, skill tree, player skill lists) using Caffeine to eliminate repeated database queries during battle turns.

**Architecture:** Add `@EnableCaching` to the application entry point, create `CacheConfig` with named Caffeine caches via `SimpleCacheManager` + `CaffeineCache`, and annotate specific service methods with `@Cacheable` / `@CacheEvict`. Static tables (weapon_size_modifiers, skill_buff_effects, skill tree) use infinite TTL. Player-specific skill data uses 5-minute TTL and is evicted on `aprenderSkill()`.

**Tech Stack:** Spring Cache abstraction, Caffeine (version managed by Spring Boot BOM).

---

## File Map

| Action | File |
|---|---|
| Modify | `pom.xml` |
| Modify | `src/main/java/com/ragnarok/RagnarokCoreApplication.java` |
| Create | `src/main/java/com/ragnarok/infrastructure/config/CacheConfig.java` |
| Modify | `src/main/java/com/ragnarok/application/service/WeaponSizeService.java` |
| Modify | `src/main/java/com/ragnarok/application/service/SkillService.java` |
| Modify | `src/main/java/com/ragnarok/infrastructure/persistence/SkillBuffEffectRepository.java` |
| Modify | `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java` |
| Create | `src/test/java/com/ragnarok/application/service/CacheVerificationTest.java` |

---

## Task 1: Add Caffeine dependency and enable caching

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/ragnarok/RagnarokCoreApplication.java`

- [ ] **Step 1: Add Caffeine dependency**

Inside `<dependencies>` in `pom.xml` (no version — managed by Spring Boot BOM):

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

- [ ] **Step 2: Add @EnableCaching to application entry point**

Read `src/main/java/com/ragnarok/RagnarokCoreApplication.java` first. Then add **only** `@EnableCaching` and its import — preserve all existing annotations unchanged:

```java
import org.springframework.cache.annotation.EnableCaching;

// Add to the class-level annotations — keep all existing annotations:
@EnableCaching
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/java/com/ragnarok/RagnarokCoreApplication.java
git commit -m "build: add Caffeine and enable Spring Cache"
```

---

## Task 2: Create CacheConfig

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/config/CacheConfig.java`

`SimpleCacheManager` with individual `CaffeineCache` instances is used here — **not** `CaffeineCacheManager.setCacheLoader()`, which has a different type signature (it takes a Caffeine `CacheLoader` for loading values, not per-cache configuration). `SimpleCacheManager` + `CaffeineCache` is the correct approach for named caches with different TTL settings.

- [ ] **Step 1: Create CacheConfig**

`src/main/java/com/ragnarok/infrastructure/config/CacheConfig.java`:
```java
package com.ragnarok.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Named caches with their expiry policies.
     *
     * Static game tables (weapon size modifiers, skill buff effects, skill tree)
     * never change at runtime — no TTL, bounded size.
     *
     * Player-specific data (learned skills) changes when a skill is learned —
     * 5-minute TTL as a safety net, with explicit eviction on aprenderSkill().
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("weaponSizeModifiers",
                        Caffeine.newBuilder().maximumSize(1000).recordStats()),
                buildCache("skillBuffEffects",
                        Caffeine.newBuilder().maximumSize(1000).recordStats()),
                buildCache("skillTree",
                        Caffeine.newBuilder().maximumSize(100).recordStats()),
                buildCache("playerSkills",
                        Caffeine.newBuilder()
                                .expireAfterWrite(5, TimeUnit.MINUTES)
                                .maximumSize(500)
                                .recordStats())
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, Caffeine<Object, Object> caffeine) {
        return new CaffeineCache(name, caffeine.build());
    }
}
```

**Cache names:**
- `weaponSizeModifiers` — results of `WeaponSizeService.getModifier()` — static, no TTL
- `skillBuffEffects` — results of `SkillBuffEffectRepository.findBySkillId()` — static, no TTL
- `skillTree` — results of the skill tree query in `SkillService` — static, no TTL
- `playerSkills` — results of `SkillService.listarSkillsDoPlayer()` — 5 min TTL, evicted on learn

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/config/CacheConfig.java
git commit -m "feat(cache): add CacheConfig with Caffeine named caches"
```

---

## Task 3: Annotate services with @Cacheable

**Files:**
- Modify: `src/main/java/com/ragnarok/application/service/WeaponSizeService.java`
- Modify: `src/main/java/com/ragnarok/application/service/SkillService.java`
- Modify: `src/main/java/com/ragnarok/infrastructure/persistence/SkillBuffEffectRepository.java`
- Modify: `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java`

- [ ] **Step 1: Annotate WeaponSizeService.getModifier()**

Read the current file first. Then add `@Cacheable("weaponSizeModifiers")` to `getModifier()`.

The cache key defaults to all parameters combined: `(weaponType, monsterSize)`. This is correct — `WeaponType` is an enum with natural equality, `monsterSize` is a String.

```java
import org.springframework.cache.annotation.Cacheable;

// Add to the method signature:
@Cacheable("weaponSizeModifiers")
public int getModifier(WeaponType weaponType, String monsterSize) {
    // existing implementation unchanged
}
```

- [ ] **Step 2: Annotate SkillService — skill list and skill learn**

Read `src/main/java/com/ragnarok/application/service/SkillService.java` fully first.

Add these two annotations:

```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

// listarSkillsDoPlayer — cache per playerId:
@Cacheable(value = "playerSkills", key = "#playerId")
public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
    // existing implementation unchanged
}

// aprenderSkill — evict the player's cached skill list after learning:
@Transactional
@CacheEvict(value = "playerSkills", key = "#playerId")
public String aprenderSkill(Long playerId, String aegisName) {
    // existing implementation unchanged
}
```

**Note:** `listarSkillsUsaveisForaDeCombate()` is deliberately NOT cached — it is called infrequently (out-of-combat skill menu only) and filtering logic changes with player state.

- [ ] **Step 3: Annotate SkillTreeRepository.findByJobClassesIn()**

The skill tree data is static game configuration loaded from rAthena. It is queried via `SkillTreeRepository.findByJobClassesIn(List<String>)` in `SkillService`. Cache it at the repository layer so it is served from Caffeine on every repeated call regardless of which service calls it.

```java
import org.springframework.cache.annotation.Cacheable;

// Add to SkillTreeRepository interface:
@Cacheable("skillTree")
List<SkillTreeEntity> findByJobClassesIn(@Param("upperJobClasses") List<String> upperJobClasses);
```

The cache key defaults to the `upperJobClasses` list. Caffeine uses in-memory storage so `List<String>` keys work without serialization. The `smallPct`, `mediumPct`, `largePct` columns are read-only game data — no eviction needed.

- [ ] **Step 4: Annotate SkillBuffEffectRepository.findBySkillId()**

Read `src/main/java/com/ragnarok/infrastructure/persistence/SkillBuffEffectRepository.java`. It is a Spring Data interface. Add `@Cacheable` directly to the method declaration.

**Why the repository (not SkillCombatService):** The spec mentions `SkillCombatService` buff lookup as the cache target, but annotating the repository is equivalent and superior — it caches the result for any future caller, not just `SkillCombatService`.

```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillBuffEffectRepository extends JpaRepository<SkillBuffEffectEntity, Long> {

    @Cacheable("skillBuffEffects")
    List<SkillBuffEffectEntity> findBySkillId(Long skillId);
}
```

Spring Data's proxy infrastructure is compatible with Spring Cache. The first call for a given `skillId` hits the database; subsequent calls return from the Caffeine cache.

- [ ] **Step 5: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/WeaponSizeService.java \
        src/main/java/com/ragnarok/application/service/SkillService.java \
        src/main/java/com/ragnarok/infrastructure/persistence/SkillBuffEffectRepository.java \
        src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java
git commit -m "feat(cache): annotate services with @Cacheable and @CacheEvict"
```

---

## Task 4: Write CacheVerificationTest

**Files:**
- Create: `src/test/java/com/ragnarok/application/service/CacheVerificationTest.java`

This test verifies that a second call to `WeaponSizeService.getModifier()` does NOT trigger an additional database query — proving the cache is working.

The test seeds the `weapon_size_modifiers` table in `@BeforeEach` to ensure deterministic behavior. Without seeded data, `getModifier()` falls through to `defaultModifier()` without calling `findByWeaponType()`, making the call-count assertion meaningless.

- [ ] **Step 1: Confirm entity structure (already verified during plan authoring)**

`WeaponSizeModifierEntity` has these fields (verified from the source):
- `weaponType` (String, @Id) — e.g., `"SWORD"`, `"BOW"`, `"NONE"`
- `smallPct` (int) — modifier for Small monsters
- `mediumPct` (int) — modifier for Medium monsters
- `largePct` (int) — modifier for Large monsters

`WeaponSizeService.getModifier()` calls `repository.findByWeaponType(weaponType.name())` and picks from `smallPct`/`mediumPct`/`largePct` based on the size string.

- [ ] **Step 2: Write the test**

`src/test/java/com/ragnarok/application/service/CacheVerificationTest.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierEntity;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies that Spring Cache is active and prevents repeated database queries
 * for static game data.
 *
 * Uses @SpyBean to count actual repository invocations.
 * Seeds the database in @BeforeEach for deterministic call-count assertions.
 */
class CacheVerificationTest extends AbstractIntegrationTest {

    @Autowired
    WeaponSizeService weaponSizeService;

    @SpyBean
    WeaponSizeModifierRepository weaponSizeModifierRepository;

    @Autowired
    WeaponSizeModifierRepository weaponSizeModifierRepositoryDirect;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear cache before each test to ensure clean state
        cacheManager.getCache("weaponSizeModifiers").clear();

        // Seed the table so findByWeaponType is actually called (not short-circuited to default).
        // WeaponSizeModifierEntity uses @AllArgsConstructor: (weaponType, smallPct, mediumPct, largePct)
        if (weaponSizeModifierRepositoryDirect.count() == 0) {
            weaponSizeModifierRepositoryDirect.save(
                    new WeaponSizeModifierEntity("SWORD", 75, 100, 75));
        }
    }

    @Test
    @DisplayName("Cache: getModifier() should query the database only on the first call")
    void getModifier_shouldHitDatabaseOnlyOnce() {
        // First call — cache miss, hits the database
        weaponSizeService.getModifier(WeaponType.SWORD, "Medium");
        // Second call with same args — cache hit, no database call
        weaponSizeService.getModifier(WeaponType.SWORD, "Medium");

        // Repository should have been called exactly once (only the first call)
        verify(weaponSizeModifierRepository, times(1)).findByWeaponType("SWORD");
    }

    @Test
    @DisplayName("Cache: cache manager should have all expected caches configured")
    void cacheManager_shouldHaveExpectedCaches() {
        assertNotNull(cacheManager.getCache("weaponSizeModifiers"));
        assertNotNull(cacheManager.getCache("skillBuffEffects"));
        assertNotNull(cacheManager.getCache("skillTree"));
        assertNotNull(cacheManager.getCache("playerSkills"));
    }
}
```

**Note on AbstractIntegrationTest dependency:** This test extends `AbstractIntegrationTest` from the Testcontainers plan (Feature B). If Feature B has not yet been implemented and `AbstractIntegrationTest` does not exist, use this standalone alternative instead:

```java
// Replace: class CacheVerificationTest extends AbstractIntegrationTest {
// With:
@SpringBootTest
@ActiveProfiles("test")
class CacheVerificationTest {

    @MockBean
    @SuppressWarnings("unused")
    com.ragnarok.runner.RagnarokTerminalRunner ragnarokTerminalRunner;
    // ... rest of the class unchanged
}
```

This requires a local PostgreSQL on `localhost:5432` with the `ragnarok_test` database. Once Feature B is complete, switch back to `extends AbstractIntegrationTest`.

- [ ] **Step 3: Run the test**

```bash
./mvnw test -Dtest=CacheVerificationTest -q
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

If `findByWeaponType` is called 0 times instead of 1, the seed data lookup key doesn't match — check the `WeaponType` enum name used as the string key in `findByWeaponType`.

- [ ] **Step 4: Run full test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/ragnarok/application/service/CacheVerificationTest.java
git commit -m "test(cache): add CacheVerificationTest — verifies cache prevents repeated DB queries"
```

---

## Task 5: Final verification

- [ ] **Step 1: Run full test suite with coverage**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`, JaCoCo ≥ 85% line / ≥ 62% branch.

- [ ] **Step 2: Final commit**

```bash
git add .
git commit -m "feat(cache): Spring Cache with Caffeine complete — static data cached, playerSkills cached with eviction"
```
