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
