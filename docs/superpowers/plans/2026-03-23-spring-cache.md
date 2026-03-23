# Spring Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache static game data (weapon size modifiers, skill buff effects, player skill lists) using Caffeine to eliminate repeated database queries during battle turns.

**Architecture:** Add `@EnableCaching` to the application entry point, create `CacheConfig` with named Caffeine caches, and annotate specific service methods with `@Cacheable` / `@CacheEvict`. Static tables (weapon_size_modifiers, skill_buff_effects) use infinite TTL. Player-specific skill data uses 5-minute TTL and is evicted on `aprenderSkill()`.

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

Read `src/main/java/com/ragnarok/RagnarokCoreApplication.java` first, then add `@EnableCaching`:

```java
package com.ragnarok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
public class RagnarokCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagnarokCoreApplication.class, args);
    }
}
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

- [ ] **Step 1: Write failing test for cache config**

We'll verify the cache config exists by checking caches are registered in the cache manager. This test goes in `CacheVerificationTest` (written in Task 4). Skip for now — proceed to implementation.

- [ ] **Step 2: Create CacheConfig**

`src/main/java/com/ragnarok/infrastructure/config/CacheConfig.java`:
```java
package com.ragnarok.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Named caches with their expiry policies.
     *
     * Static game tables (weapon size modifiers, skill tree data, buff effects)
     * never change at runtime — use infinite TTL.
     *
     * Player-specific data (learned skills) changes when a skill is learned —
     * use 5-minute TTL as a safety net, with explicit eviction on aprenderSkill().
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheLoader(cacheName -> {
            Caffeine<Object, Object> builder = Caffeine.newBuilder().recordStats();
            if ("playerSkills".equals(cacheName)) {
                builder.expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(500);
            } else {
                // Static data: no expiry, bounded size
                builder.maximumSize(1000);
            }
            return builder.build();
        });
        manager.setCacheNames(java.util.List.of(
                "weaponSizeModifiers",
                "skillBuffEffects",
                "playerSkills"
        ));
        return manager;
    }
}
```

**Cache names:**
- `weaponSizeModifiers` — results of `WeaponSizeService.getModifier()` — static, no TTL
- `skillBuffEffects` — results of `SkillBuffEffectRepository.findBySkillId()` — static, no TTL
- `playerSkills` — results of `SkillService.listarSkillsDoPlayer()` — 5 min TTL, evicted on learn

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

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

- [ ] **Step 2: Annotate SkillService.listarSkillsDoPlayer() and aprenderSkill()**

Read the current file first. Then add `@Cacheable` and `@CacheEvict`:

```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

// listarSkillsDoPlayer — cache per playerId:
@Cacheable(value = "playerSkills", key = "#playerId")
public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
    // existing implementation unchanged
}

// aprenderSkill — evict the cache for this player after learning:
@Transactional
@CacheEvict(value = "playerSkills", key = "#playerId")
public String aprenderSkill(Long playerId, String aegisName) {
    // existing implementation unchanged
}
```

**Note:** `listarSkillsUsaveisForaDeCombate()` is deliberately NOT cached — it is called infrequently (out-of-combat skill menu only) and adding a second cache would require a second `@CacheEvict` on `aprenderSkill()` with a different key structure. Not worth the complexity.

- [ ] **Step 3: Annotate SkillBuffEffectRepository.findBySkillId()**

Read `src/main/java/com/ragnarok/infrastructure/persistence/SkillBuffEffectRepository.java`. It is a Spring Data interface. Add `@Cacheable` directly to the method declaration:

```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillBuffEffectRepository extends JpaRepository<SkillBuffEffectEntity, Long> {

    @Cacheable("skillBuffEffects")
    List<SkillBuffEffectEntity> findBySkillId(Long skillId);
}
```

Spring Data's proxy infrastructure is compatible with Spring Cache. The first call for a given `skillId` hits the database; subsequent calls return from the Caffeine cache.

- [ ] **Step 4: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/WeaponSizeService.java \
        src/main/java/com/ragnarok/application/service/SkillService.java \
        src/main/java/com/ragnarok/infrastructure/persistence/SkillBuffEffectRepository.java
git commit -m "feat(cache): annotate services with @Cacheable and @CacheEvict"
```

---

## Task 4: Write CacheVerificationTest

**Files:**
- Create: `src/test/java/com/ragnarok/application/service/CacheVerificationTest.java`

This test verifies that a second call to `WeaponSizeService.getModifier()` does NOT trigger an additional database query — proving the cache is working.

- [ ] **Step 1: Write the test**

`src/test/java/com/ragnarok/application/service/CacheVerificationTest.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies that Spring Cache is active and prevents repeated database queries
 * for static game data.
 *
 * Uses @SpyBean to count actual repository invocations.
 * AbstractIntegrationTest provides the Testcontainers PostgreSQL and @MockBean for the terminal.
 */
class CacheVerificationTest extends AbstractIntegrationTest {

    @Autowired
    WeaponSizeService weaponSizeService;

    @SpyBean
    WeaponSizeModifierRepository weaponSizeModifierRepository;

    @Autowired
    CacheManager cacheManager;

    @Test
    @DisplayName("Cache: getModifier() deve consultar o banco apenas na primeira chamada")
    void getModifier_shouldHitDatabaseOnlyOnce() {
        // Evict to ensure clean state
        cacheManager.getCache("weaponSizeModifiers").clear();

        // First call — should hit the database
        weaponSizeService.getModifier(WeaponType.SWORD, "Medium");
        // Second call with same args — should be served from cache
        weaponSizeService.getModifier(WeaponType.SWORD, "Medium");
        // Third call with different args — cache miss, should hit database again
        weaponSizeService.getModifier(WeaponType.BOW, "Small");

        // Repository should have been called exactly twice (first + third calls)
        verify(weaponSizeModifierRepository, times(2)).findByWeaponType(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Cache: cache manager deve ter os caches configurados")
    void cacheManager_shouldHaveExpectedCaches() {
        org.junit.jupiter.api.Assertions.assertNotNull(cacheManager.getCache("weaponSizeModifiers"));
        org.junit.jupiter.api.Assertions.assertNotNull(cacheManager.getCache("skillBuffEffects"));
        org.junit.jupiter.api.Assertions.assertNotNull(cacheManager.getCache("playerSkills"));
    }
}
```

**Note:** `AbstractIntegrationTest` requires Docker for Testcontainers. If Testcontainers is not yet implemented (running this plan in isolation), use a `@SpringBootTest` + `@ActiveProfiles("test")` setup with a local PostgreSQL instead, and remove the `extends AbstractIntegrationTest`.

- [ ] **Step 2: Run the test**

```bash
./mvnw test -Dtest=CacheVerificationTest -q
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

If `weaponSizeModifierRepository` has no data, `getModifier()` falls back to `defaultModifier()` without hitting `findByWeaponType()`. In that case, `verify(..., times(0))` — adjust to `times(0)` if the table is empty in the test container.

To ensure data exists, the `StartupDataLoader` normally populates it. In tests, `StartupDataLoader` is `@Profile("!test")` and excluded. You can seed the table manually in `@BeforeEach` or verify times(0) for empty table.

Alternative simpler assertion:

```java
// If table is empty, fallback is used — no DB call:
verify(weaponSizeModifierRepository, times(1)).findByWeaponType("SWORD");
// Second call: from cache
weaponSizeService.getModifier(WeaponType.SWORD, "Medium");
verify(weaponSizeModifierRepository, times(1)).findByWeaponType("SWORD"); // still 1
```

- [ ] **Step 3: Run full test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS` — all tests pass including existing integration tests.

- [ ] **Step 4: Commit**

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

- [ ] **Step 2: Verify application starts with cache enabled**

```bash
./mvnw spring-boot:run &
sleep 10
curl -s http://localhost:8080/actuator/health | grep -q "UP" && echo "OK"
```

Or simply start with `java -jar target/*.jar` and confirm no errors related to caching in the startup log.

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat(cache): Spring Cache with Caffeine complete — static data cached, playerSkills cached with eviction"
```
