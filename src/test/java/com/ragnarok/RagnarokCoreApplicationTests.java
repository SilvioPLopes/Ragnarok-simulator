package com.ragnarok;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre",
        "spring.jpa.hibernate.ddl-auto=update"
})
class RagnarokCoreApplicationTests {

    @Test
    void contextLoads() {
    }

}
