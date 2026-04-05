package com.ragnarok.runner.populator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ativa o populador de dados do cliente bRO na inicialização.
 * Só roda quando ro.assets.run-populator=true em application.properties.
 * @Order(4) — depois do RathenaImporter (1), PlayerSeed (2), StartupDataLoader (3).
 */
@Component
@Order(4)
@ConditionalOnProperty(name = "ro.assets.run-populator", havingValue = "true")
public class ClientDataRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientDataRunner.class);
    private final ItemInfoPopulator populator;

    public ClientDataRunner(ItemInfoPopulator populator) {
        this.populator = populator;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== CLIENT DATA POPULATOR INICIADO ===");
        populator.run();
        log.info("=== CLIENT DATA POPULATOR CONCLUIDO ===");
    }
}
