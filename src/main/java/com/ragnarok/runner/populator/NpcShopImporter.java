package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.infrastructure.persistence.NpcEntity;
import com.ragnarok.infrastructure.persistence.NpcRepository;
import com.ragnarok.infrastructure.persistence.NpcShopItemEntity;
import com.ragnarok.infrastructure.persistence.NpcShopItemRepository;
import com.ragnarok.infrastructure.persistence.NpcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class NpcShopImporter {

    private static final Logger log = LoggerFactory.getLogger(NpcShopImporter.class);

    private final NpcRepository npcRepository;
    private final NpcShopItemRepository shopItemRepository;
    private final ItemRepository itemRepository;

    public NpcShopImporter(NpcRepository npcRepository,
                           NpcShopItemRepository shopItemRepository,
                           ItemRepository itemRepository) {
        this.npcRepository = npcRepository;
        this.shopItemRepository = shopItemRepository;
        this.itemRepository = itemRepository;
    }

    public void run() throws IOException {
        boolean skipItems = shopItemRepository.count() > 500;
        if (skipItems) {
            log.info("NpcShopImporter: npc_shop_items já populada ({}), apenas atualizando sprite URLs.",
                    shopItemRepository.count());
        }

        ClassPathResource resource = new ClassPathResource("rathena/shops.txt");
        int npcsCreated = 0;
        int npcsUpdated = 0;
        int itemsInserted = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.isBlank()) continue;

                String[] cols = line.split("\t");
                if (cols.length < 4 || !"shop".equals(cols[1].trim())) continue;

                String[] locationParts = cols[0].split(",");
                if (locationParts.length < 3) continue;

                String mapName;
                int x, y;
                try {
                    mapName = locationParts[0].trim();
                    x = Integer.parseInt(locationParts[1].trim());
                    y = Integer.parseInt(locationParts[2].trim());
                } catch (NumberFormatException e) {
                    continue;
                }

                String npcNameRaw = cols[2].trim();
                String npcName = npcNameRaw.contains("#")
                        ? npcNameRaw.substring(0, npcNameRaw.indexOf('#'))
                        : npcNameRaw;

                String[] itemTokens = cols[3].split(",");
                if (itemTokens.length < 2) continue;

                // Primeiro token = spriteId do NPC
                int spriteId = 0;
                try {
                    spriteId = Integer.parseInt(itemTokens[0].trim());
                } catch (NumberFormatException ignored) {}
                final int finalSpriteId = spriteId;
                final String spriteUrl = finalSpriteId > 0
                        ? "/ro-assets/output-npcs/" + finalSpriteId + "/0-0.png"
                        : null;

                String seedId = "shop_" + mapName + "_" + x + "_" + y;
                boolean existed = npcRepository.existsBySeedId(seedId);
                NpcEntity npc = npcRepository.findBySeedId(seedId).orElseGet(() -> {
                    NpcEntity newNpc = new NpcEntity();
                    newNpc.setSeedId(seedId);
                    newNpc.setName(npcName);
                    newNpc.setType(NpcType.SHOP);
                    newNpc.setMapName(mapName);
                    newNpc.setX(x);
                    newNpc.setY(y);
                    newNpc.setSpriteRef(null);
                    newNpc.setSpriteUrl(spriteUrl);
                    return npcRepository.save(newNpc);
                });

                if (!existed) {
                    npcsCreated++;
                } else if (npc.getSpriteUrl() == null && spriteUrl != null) {
                    npc.setSpriteUrl(spriteUrl);
                    npcRepository.save(npc);
                    npcsUpdated++;
                }

                if (!skipItems) {
                    for (int i = 1; i < itemTokens.length; i++) {
                        String token = itemTokens[i].trim();
                        String[] parts = token.split(":");
                        if (parts.length < 2) continue;
                        long itemId;
                        int price;
                        try {
                            itemId = Long.parseLong(parts[0].trim());
                            price = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        if (shopItemRepository.findByNpcIdAndItemId(npc.getId(), itemId).isPresent()) continue;

                        String itemName = itemRepository.findById(itemId)
                                .map(item -> item.getName())
                                .orElse("Desconhecido");

                        NpcShopItemEntity shopItem = new NpcShopItemEntity();
                        shopItem.setNpc(npc);
                        shopItem.setItemId(itemId);
                        shopItem.setItemName(itemName);
                        shopItem.setPrice(price);
                        shopItemRepository.save(shopItem);
                        itemsInserted++;
                    }
                }
            }
        }

        log.info("NpcShopImporter: {} NPCs criados, {} sprite URLs atualizadas, {} itens inseridos.",
                npcsCreated, npcsUpdated, itemsInserted);
    }
}
