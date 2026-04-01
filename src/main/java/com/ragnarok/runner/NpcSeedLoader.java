package com.ragnarok.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(4)
public class NpcSeedLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NpcSeedLoader.class);

    private final NpcRepository npcRepository;
    private final NpcShopItemRepository shopItemRepository;
    private final NpcWarpDestinationRepository warpDestinationRepository;
    private final ObjectMapper objectMapper;

    public NpcSeedLoader(NpcRepository npcRepository,
                         NpcShopItemRepository shopItemRepository,
                         NpcWarpDestinationRepository warpDestinationRepository,
                         ObjectMapper objectMapper) {
        this.npcRepository = npcRepository;
        this.shopItemRepository = shopItemRepository;
        this.warpDestinationRepository = warpDestinationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        ClassPathResource resource = new ClassPathResource("prontera_npcs_seed.json");
        if (!resource.exists()) {
            log.warn("prontera_npcs_seed.json not found in classpath, skipping NPC seed.");
            return;
        }

        JsonNode root = objectMapper.readTree(resource.getInputStream());
        int created = 0;

        for (JsonNode node : root) {
            String seedId = node.get("id").asText();
            if (npcRepository.existsBySeedId(seedId)) {
                continue;
            }

            NpcEntity npc = new NpcEntity();
            npc.setSeedId(seedId);
            npc.setName(node.get("name").asText());
            npc.setType(NpcType.valueOf(node.get("type").asText()));
            npc.setX(node.get("x").asInt());
            npc.setY(node.get("y").asInt());
            npc.setMapName(node.get("map").asText());
            npc.setSpriteRef(node.has("spriteRef") ? node.get("spriteRef").asText() : null);
            npcRepository.save(npc);

            if (node.has("shopItems")) {
                for (JsonNode si : node.get("shopItems")) {
                    NpcShopItemEntity shopItem = new NpcShopItemEntity();
                    shopItem.setNpc(npc);
                    shopItem.setItemId(si.get("itemId").asLong());
                    shopItem.setItemName(si.get("itemName").asText());
                    shopItem.setPrice(si.get("price").asInt());
                    shopItemRepository.save(shopItem);
                }
            }

            if (node.has("destinations")) {
                for (JsonNode dest : node.get("destinations")) {
                    NpcWarpDestinationEntity warp = new NpcWarpDestinationEntity();
                    warp.setNpc(npc);
                    warp.setMapName(dest.get("mapName").asText());
                    warp.setX(dest.get("x").asInt());
                    warp.setY(dest.get("y").asInt());
                    warpDestinationRepository.save(warp);
                }
            }

            created++;
        }

        log.info("NPC seed: {} NPC(s) inseridos.", created);
    }
}
