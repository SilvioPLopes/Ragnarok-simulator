package com.ragnarok;

import com.ragnarok.runner.RagnarokTerminalRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests.
 *
 * Uses the Singleton Container pattern: the PostgreSQL container is started once
 * via static initializer and lives for the entire JVM run, shared across all
 * subclasses. This prevents Testcontainers from stopping/restarting the container
 * between test classes, which would cause port changes and break the cached
 * Spring application context.
 *
 * @MockBean RagnarokTerminalRunner suppresses the interactive terminal loop during tests.
 * Subclasses must NOT re-declare this mock.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    RagnarokTerminalRunner ragnarokTerminalRunner;

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
