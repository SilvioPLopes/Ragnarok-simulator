package com.ragnarok;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RagnarokCoreApplicationTests {

    @Test
    void contextLoads() {
    }

}
