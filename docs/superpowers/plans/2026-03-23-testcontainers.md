# Testcontainers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded `localhost:5432` PostgreSQL dependency in integration tests with Testcontainers, so `./mvnw test` works on any machine with Docker — no local PostgreSQL required.

**Architecture:** Create an abstract base class `AbstractIntegrationTest` with `@Testcontainers`, a shared `static PostgreSQLContainer`, and `@DynamicPropertySource` to override datasource properties. All 8 existing integration test classes extend this base and remove their individual `@MockBean RagnarokTerminalRunner` declarations (moved to base).

**Tech Stack:** Testcontainers `postgresql` + `junit-jupiter` (versions managed by Spring Boot BOM), JUnit 5, Spring Boot Test.

---

## File Map

| Action | File |
|---|---|
| Modify | `pom.xml` |
| Create | `src/test/java/com/ragnarok/AbstractIntegrationTest.java` |
| Modify | `src/test/resources/application-test.properties` |
| Modify | `src/test/java/com/ragnarok/infrastructure/persistence/BattleIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/infrastructure/persistence/BattleLootIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/infrastructure/persistence/MonsterDropIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/infrastructure/persistence/PlayerInventoryIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/infrastructure/persistence/MapSpawnIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/application/service/ItemServiceIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java` |
| Modify | `src/test/java/com/ragnarok/application/service/ClassChangeIntegrationTest.java` |

---

## Task 1: Add Testcontainers dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependencies**

Inside `<dependencies>` in `pom.xml`, add (no version needed — Spring Boot BOM manages them):

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

- [ ] **Step 2: Verify dependency resolution**

```bash
./mvnw dependency:resolve -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add testcontainers postgresql and junit-jupiter dependencies"
```

---

## Task 2: Create AbstractIntegrationTest base class

**Files:**
- Create: `src/test/java/com/ragnarok/AbstractIntegrationTest.java`

- [ ] **Step 1: Create the base class**

`src/test/java/com/ragnarok/AbstractIntegrationTest.java`:
```java
package com.ragnarok;

import com.ragnarok.runner.RagnarokTerminalRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests.
 *
 * Starts a single PostgreSQL container shared across all test classes in the same JVM run.
 * The container is declared static, so it is initialized once per test suite execution.
 *
 * @MockBean RagnarokTerminalRunner suppresses the interactive terminal loop during tests.
 * Subclasses must NOT re-declare this mock.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    RagnarokTerminalRunner ragnarokTerminalRunner;

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**Why `static final`:** A static container is shared for the entire test suite JVM run — only one PostgreSQL startup cost. Each test class that extends `AbstractIntegrationTest` reuses the same running container.

**Why `@ActiveProfiles("test")`:** This activates `application-test.properties` which keeps `flyway.enabled=false`, `ddl-auto=update`, and reduced logging. `@Profile("!test")` beans like `RathenaImporter` and `StartupDataLoader` are excluded.

**Why no `withReuse(true)`:** Container reuse requires `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` on each developer machine, which is not guaranteed in a public project. The `static` field already provides suite-level container sharing within one JVM run.

- [ ] **Step 2: Verify compilation**

```bash
./mvnw test-compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/ragnarok/AbstractIntegrationTest.java
git commit -m "test: add AbstractIntegrationTest with Testcontainers PostgreSQL"
```

---

## Task 3: Update application-test.properties

**Files:**
- Modify: `src/test/resources/application-test.properties`

Current file content:
```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:ragnarok_test}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:postgre}
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=false
spring.datasource.hikari.maximum-pool-size=3
spring.datasource.hikari.minimum-idle=1
spring.jpa.show-sql=false
logging.level.root=ERROR
logging.level.com.ragnarok=WARN
```

- [ ] **Step 1: Remove datasource URL, username, and password lines**

These 3 lines are replaced by `@DynamicPropertySource` in `AbstractIntegrationTest`. The remaining settings still apply.

New content of `src/test/resources/application-test.properties`:
```properties
# Datasource URL/username/password are set dynamically by AbstractIntegrationTest via Testcontainers.

spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=false
spring.datasource.hikari.maximum-pool-size=3
spring.datasource.hikari.minimum-idle=1
spring.jpa.show-sql=false
logging.level.root=ERROR
logging.level.com.ragnarok=WARN
```

- [ ] **Step 2: Commit**

```bash
git add src/test/resources/application-test.properties
git commit -m "test: remove hardcoded datasource from application-test.properties"
```

---

## Task 4: Migrate all integration tests to extend AbstractIntegrationTest

**Files:**
- Modify all 8 `*IntegrationTest.java` files

Each test class needs:
1. Remove `@SpringBootTest` annotation (inherited from `AbstractIntegrationTest`)
2. Remove `@ActiveProfiles("test")` annotation (inherited)
3. Remove `@MockBean private RagnarokTerminalRunner ragnarokTerminalRunner` declaration (moved to base)
4. Add `extends AbstractIntegrationTest`
5. Keep all other annotations like `@TestPropertySource`, `@Transactional`, `@Autowired` etc.

- [ ] **Step 1: Migrate BattleIntegrationTest**

In `src/test/java/com/ragnarok/infrastructure/persistence/BattleIntegrationTest.java`:
- Remove: `@ActiveProfiles("test")`, `@SpringBootTest`, `@MockBean ... ragnarokTerminalRunner` field
- Add `extends AbstractIntegrationTest` to the class declaration
- Add import: `import com.ragnarok.AbstractIntegrationTest;`

- [ ] **Step 2: Migrate remaining 7 integration tests**

Apply the same changes to:
- `BattleLootIntegrationTest.java`
- `MonsterDropIntegrationTest.java`
- `PlayerInventoryIntegrationTest.java`
- `MapSpawnIntegrationTest.java`
- `ItemServiceIntegrationTest.java`
- `SkillServiceIntegrationTest.java`
- `ClassChangeIntegrationTest.java`

Pattern for each class (replace `XxxIntegrationTest` with actual class name):

```java
// BEFORE:
@ActiveProfiles("test")
@SpringBootTest
class XxxIntegrationTest {
    @MockBean
    @SuppressWarnings("unused")
    private RagnarokTerminalRunner ragnarokTerminalRunner;
    ...
}

// AFTER:
import com.ragnarok.AbstractIntegrationTest;

class XxxIntegrationTest extends AbstractIntegrationTest {
    // ragnarokTerminalRunner mock is in AbstractIntegrationTest
    ...
}
```

**Important:** Any `@TestPropertySource` annotations on individual test classes should be kept as-is. The base class provides the container datasource via `@DynamicPropertySource` which takes precedence over properties files.

- [ ] **Step 3: Run full test suite — first run (Docker required)**

```bash
./mvnw test -q
```

Expected: All 212 tests pass. First run takes ~30s for Docker to pull `postgres:16`. Subsequent runs are faster.

If Docker is not running, you will see:
```
Could not find a valid Docker environment
```
Start Docker Desktop and retry.

- [ ] **Step 4: Verify no local PostgreSQL is needed**

Stop any local PostgreSQL service and re-run:
```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS` — tests pass without local PostgreSQL.

- [ ] **Step 5: Commit**

```bash
git add src/test/
git commit -m "test: migrate all integration tests to Testcontainers via AbstractIntegrationTest"
```

---

## Task 5: Final verification

- [ ] **Step 1: Run full test suite with JaCoCo**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`, all 212+ tests pass, JaCoCo ≥ 85% line / ≥ 62% branch.

- [ ] **Step 2: Verify CI pipeline still works**

Check `.github/workflows/ci.yml` — the CI pipeline uses a PostgreSQL service container. With Testcontainers, the tests bring their own PostgreSQL and the service container is redundant. However, keeping both does not break anything (Testcontainers will use Docker-in-Docker or the host Docker socket on the CI runner).

If the CI runner supports Docker (GitHub Actions ubuntu runners do), `./mvnw test` will work without the service container. The service container in `ci.yml` can optionally be removed in a follow-up PR.

- [ ] **Step 3: Final commit if any remaining changes**

```bash
git status
git add -A
git commit -m "test: testcontainers migration complete — ./mvnw test works without local PostgreSQL"
```
