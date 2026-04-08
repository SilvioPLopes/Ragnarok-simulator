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
    private final NaviMapPopulator naviMapPopulator;
    private final NaviNpcPopulator naviNpcPopulator;
    private final NaviLinkPopulator naviLinkPopulator;
    private final NpcShopImporter npcShopImporter;
    private final SkillClientDataPopulator skillClientDataPopulator;
    private final NaviMobPopulator naviMobPopulator;
    private final RathenaDialogPopulator rathenaDialogPopulator;

    public ClientDataRunner(ItemInfoPopulator itemInfoPopulator,
                            NpcSpritePopulator npcSpritePopulator,
                            NaviMapPopulator naviMapPopulator,
                            NaviNpcPopulator naviNpcPopulator,
                            NaviLinkPopulator naviLinkPopulator,
                            NpcShopImporter npcShopImporter,
                            SkillClientDataPopulator skillClientDataPopulator,
                            NaviMobPopulator naviMobPopulator,
                            RathenaDialogPopulator rathenaDialogPopulator) {
        this.itemInfoPopulator = itemInfoPopulator;
        this.npcSpritePopulator = npcSpritePopulator;
        this.naviMapPopulator = naviMapPopulator;
        this.naviNpcPopulator = naviNpcPopulator;
        this.naviLinkPopulator = naviLinkPopulator;
        this.npcShopImporter = npcShopImporter;
        this.skillClientDataPopulator = skillClientDataPopulator;
        this.naviMobPopulator = naviMobPopulator;
        this.rathenaDialogPopulator = rathenaDialogPopulator;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== CLIENT DATA POPULATOR INICIADO ===");
        itemInfoPopulator.run();           // 1. items: lua → PNGs → DB
        npcSpritePopulator.run();          // 2. NPC sprite URLs via npcidentity (seed NPCs)
        naviMapPopulator.run();            // 3. map display names
        naviNpcPopulator.run();            // 4. NPCs do navi — coordenadas + spriteUrl direta
        naviLinkPopulator.run();           // 5. portals/warps
        npcShopImporter.run();             // 6. NPC shop inventories + spriteUrl de lojas
        skillClientDataPopulator.run();    // 7. skill descriptions e icons
        naviMobPopulator.run();            // 8. monster spawns
        rathenaDialogPopulator.run();      // 9. diálogos rAthena + sprite fallback
        log.info("=== CLIENT DATA POPULATOR CONCLUIDO ===");
    }
}
