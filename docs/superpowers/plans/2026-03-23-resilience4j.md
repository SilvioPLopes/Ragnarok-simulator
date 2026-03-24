# Resilience4j Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add retry with exponential backoff and circuit breaker to the rAthena data download, so network failures during startup produce clear warnings and graceful fallback instead of silent failures.

**Architecture:** Extract the HTTP download logic from `RathenaImporter` into a new `RathenaDownloadService` bean. Apply `@Retry` and `@CircuitBreaker` on the download method in that new bean. `RathenaImporter` injects and calls `RathenaDownloadService` — Spring's AOP proxy correctly intercepts calls between beans. The fallback logs a warning and returns `null`; `RathenaImporter` checks for `null` and skips the import gracefully.

**Tech Stack:** Resilience4j Spring Boot 3 starter (`resilience4j-spring-boot3`), Spring AOP.

---

## File Map

| Action | File |
|---|---|
| Modify | `pom.xml` |
| Create | `src/main/java/com/ragnarok/runner/importer/RathenaDownloadService.java` |
| Modify | `src/main/java/com/ragnarok/runner/importer/RathenaImporter.java` |
| Modify | `src/main/resources/application.properties` |

> **Note:** `com/ragnarok/runner/importer/**` is excluded from JaCoCo in `pom.xml`, so no additional test coverage is required for these files.

---

## Task 1: Add Resilience4j dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependency**

Inside `<dependencies>` in `pom.xml`:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

Resilience4j version is managed by Spring Cloud BOM (already in `pom.xml` via `spring-cloud-dependencies`). No explicit version needed.

Also add the Spring AOP starter if not already present (required for annotation-based interception):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

- [ ] **Step 2: Verify dependency resolution**

```bash
./mvnw dependency:resolve -q
```

Expected: `BUILD SUCCESS`. If version conflict on `resilience4j`, explicitly pin: check `./mvnw dependency:tree -Dincludes=io.github.resilience4j` and align versions.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add resilience4j-spring-boot3 and spring-boot-starter-aop"
```

---

## Task 2: Add Resilience4j configuration to application.properties

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Read current application.properties to understand existing content**

Read `src/main/resources/application.properties` before editing.

- [ ] **Step 2: Add Resilience4j configuration**

Append to `application.properties`:

```properties
# Resilience4j — rAthena import retry and circuit breaker
resilience4j.retry.instances.rathena.max-attempts=3
resilience4j.retry.instances.rathena.wait-duration=2s
resilience4j.retry.instances.rathena.enable-exponential-backoff=true
resilience4j.retry.instances.rathena.exponential-backoff-multiplier=2

resilience4j.circuitbreaker.instances.rathena.sliding-window-size=3
resilience4j.circuitbreaker.instances.rathena.failure-rate-threshold=100
resilience4j.circuitbreaker.instances.rathena.wait-duration-in-open-state=30s
```

**What these settings mean:**
- `max-attempts=3`: try 3 times before giving up
- `wait-duration=2s` + `exponential-backoff-multiplier=2`: wait 2s, then 4s between retries
- `sliding-window-size=3` + `failure-rate-threshold=100`: circuit opens after 3/3 failures
- `wait-duration-in-open-state=30s`: circuit stays open 30s before trying again

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "config: add Resilience4j retry and circuit breaker config for rAthena import"
```

---

## Task 3: Create RathenaDownloadService

**Files:**
- Create: `src/main/java/com/ragnarok/runner/importer/RathenaDownloadService.java`

**Why a separate bean:** Resilience4j works via Spring AOP proxy. A bean can only be intercepted when the call comes from *outside* the bean (through the proxy). If `RathenaImporter.run()` called a private `downloadYaml()` on itself, the proxy would be bypassed and the `@Retry`/`@CircuitBreaker` annotations would silently do nothing. Extracting to a separate `@Service` bean ensures every `RathenaImporter → RathenaDownloadService` call goes through the proxy.

- [ ] **Step 1: Create RathenaDownloadService**

`src/main/java/com/ragnarok/runner/importer/RathenaDownloadService.java`:
```java
package com.ragnarok.runner.importer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Downloads YAML files from rAthena GitHub with retry and circuit breaker.
 *
 * Separated from RathenaImporter so that Resilience4j AOP can intercept calls
 * correctly (proxy-based interception requires cross-bean calls).
 *
 * Excluded from test profile — same as RathenaImporter.
 */
@Service
@Profile("!test")
public class RathenaDownloadService {

    private static final Logger log = LoggerFactory.getLogger(RathenaDownloadService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Downloads the YAML content from the given URL.
     *
     * Retries up to 3 times with exponential backoff (2s, 4s).
     * If all retries fail, the circuit breaker opens and falls back to returning null.
     *
     * @param url the raw GitHub URL
     * @return YAML content as String, or null if download fails after all retries
     */
    @Retry(name = "rathena", fallbackMethod = "downloadFalhou")
    @CircuitBreaker(name = "rathena")
    public String download(String url) {
        log.debug("Downloading: {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    /**
     * Fallback invoked after all retries are exhausted.
     * Signature must match the decorated method with an extra Exception parameter.
     */
    private String downloadFalhou(String url, Exception e) {
        log.warn("rAthena unavailable after retries (url={}). Starting with existing data. Cause: {}",
                url, e.getMessage());
        return null;
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ragnarok/runner/importer/RathenaDownloadService.java
git commit -m "feat(resilience4j): add RathenaDownloadService with @Retry and @CircuitBreaker"
```

---

## Task 4: Update RathenaImporter to use RathenaDownloadService

**Files:**
- Modify: `src/main/java/com/ragnarok/runner/importer/RathenaImporter.java`

- [ ] **Step 1: Read the current RathenaImporter**

Read `src/main/java/com/ragnarok/runner/importer/RathenaImporter.java` before editing.

Current state: `RathenaImporter` has a `private String downloadYaml(String url)` method that creates a `RestTemplate` and calls `getForObject`. This method is called directly (no proxy interception possible).

- [ ] **Step 2: Inject RathenaDownloadService and delegate download calls**

Changes to make:
1. Add `RathenaDownloadService` to constructor parameter and field
2. Replace `private String downloadYaml(String url)` method body with a call to `rathenaDownloadService.download(url)`
3. Add null check after each download — if null, skip the import for that file

Updated `RathenaImporter.java`:

```java
package com.ragnarok.runner.importer;

import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
@Profile("!test")
public class RathenaImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RathenaImporter.class);

    private static final String MOB_DB_URL =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/mob_db.yml";
    private static final String ITEM_DB_USABLE =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/item_db_usable.yml";
    private static final String ITEM_DB_EQUIP =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/item_db_equip.yml";
    private static final String ITEM_DB_ETC =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/item_db_etc.yml";

    private final MonsterRepository monsterRepo;
    private final ItemRepository itemRepo;
    private final MobDbParser mobParser;
    private final ItemDbParser itemParser;
    private final RathenaDownloadService downloadService;

    public RathenaImporter(MonsterRepository monsterRepo,
                           ItemRepository itemRepo,
                           MobDbParser mobParser,
                           ItemDbParser itemParser,
                           RathenaDownloadService downloadService) {
        this.monsterRepo = monsterRepo;
        this.itemRepo    = itemRepo;
        this.mobParser   = mobParser;
        this.itemParser  = itemParser;
        this.downloadService = downloadService;
    }

    @Override
    public void run(String... args) {
        if (monsterRepo.count() == 0) {
            log.info("Importing monsters from rAthena...");
            String yaml = downloadService.download(MOB_DB_URL);
            if (yaml != null) {
                List<MonsterEntity> monsters = mobParser.parse(yaml);
                monsterRepo.saveAll(monsters);
                log.info("Imported {} monsters.", monsters.size());
            } else {
                log.warn("Monster import skipped — download returned null.");
            }
        } else {
            log.info("Monsters already exist in the database. Skipping import.");
        }

        if (itemRepo.count() == 0) {
            log.info("Importing items from rAthena...");
            List<ItemEntity> todos = new ArrayList<>();
            todos.addAll(parsearArquivo("Usable", ITEM_DB_USABLE));
            todos.addAll(parsearArquivo("Equip",  ITEM_DB_EQUIP));
            todos.addAll(parsearArquivo("Etc",    ITEM_DB_ETC));
            if (!todos.isEmpty()) {
                itemRepo.saveAll(todos);
                log.info("Imported {} items total.", todos.size());
            }
        } else {
            log.info("Items already exist in the database. Skipping import.");
        }
    }

    private List<ItemEntity> parsearArquivo(String nome, String url) {
        log.info("  Downloading {} items...", nome);
        String yaml = downloadService.download(url);
        if (yaml == null) {
            log.warn("  {} item download skipped — download returned null.", nome);
            return List.of();
        }
        List<ItemEntity> itens = itemParser.parse(yaml);
        log.info("  Parsed {} items from {}.", itens.size(), nome);
        return itens;
    }
}
```

**Key change:** `RestTemplate` is removed from `RathenaImporter`. All HTTP calls go through `downloadService.download(url)`. If download fails after retries, `null` is returned and the import is skipped with a clear `WARN` log.

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS` — existing tests are unaffected since `RathenaImporter` and `RathenaDownloadService` are `@Profile("!test")` and never active during tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/runner/importer/RathenaImporter.java
git commit -m "feat(resilience4j): RathenaImporter delegates downloads to RathenaDownloadService"
```

---

## Task 5: Final verification

- [ ] **Step 1: Run full test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 2: Verify startup behavior with network available**

Start the application normally. In the startup log, you should see:

```
Importing monsters from rAthena...
Downloading: https://raw.githubusercontent.com/...
Imported 2675 monsters.
```

No retry messages — network is available.

- [ ] **Step 3: Verify fallback behavior (optional)**

To test the fallback without disabling the network, temporarily change the URL in `RathenaDownloadService` to an invalid host, start, verify the `WARN` log appears, then revert.

Expected log on failure:
```
WARN  RathenaDownloadService - rAthena unavailable after retries (url=...). Starting with existing data.
WARN  RathenaImporter - Monster import skipped — download returned null.
```

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "feat(resilience4j): rAthena import hardened with retry + circuit breaker — complete"
```
