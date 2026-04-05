package com.ragnarok.runner.populator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ativa os populadores de dados do cliente bRO na inicialização.
 * Só roda quando ro.assets.run-populator=true em application.properties.
 * @Order(5) — depois do NpcSeedLoader (4). NpcSpritePopulator depende de NPCs no banco.
 */
@Component
@Order(5)
@ConditionalOnProperty(name = "ro.assets.run-populator", havingValue = "true")
public class ClientDataRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientDataRunner.class);

    private final ItemInfoPopulator itemInfoPopulator;
    private final NpcSpritePopulator npcSpritePopulator;

    public ClientDataRunner(ItemInfoPopulator itemInfoPopulator, NpcSpritePopulator npcSpritePopulator) {
        this.itemInfoPopulator = itemInfoPopulator;
        this.npcSpritePopulator = npcSpritePopulator;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== CLIENT DATA POPULATOR INICIADO ===");
        itemInfoPopulator.run();
        npcSpritePopulator.run();
        log.info("=== CLIENT DATA POPULATOR CONCLUIDO ===");
    }
}
