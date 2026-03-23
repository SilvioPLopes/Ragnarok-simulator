package com.ragnarok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableCaching
@EnableFeignClients
@SpringBootApplication
public class RagnarokCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagnarokCoreApplication.class, args);
    }

}
