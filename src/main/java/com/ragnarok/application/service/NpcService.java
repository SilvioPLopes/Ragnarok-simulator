package com.ragnarok.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.dto.response.*;
import com.ragnarok.domain.exception.GameException;
import com.ragnarok.domain.model.NpcDialogNode;
import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NpcService {

    private static final Logger log = LoggerFactory.getLogger(NpcService.class);

    private final NpcRepository npcRepository;
    private final NpcShopItemRepository shopItemRepository;
    private final NpcWarpDestinationRepository warpDestinationRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final ItemRepository itemRepository;
    private final NpcDialogRepository npcDialogRepository;
    private final ObjectMapper        objectMapper;

    public NpcService(NpcRepository npcRepository,
                      NpcShopItemRepository shopItemRepository,
                      NpcWarpDestinationRepository warpDestinationRepository,
                      PlayerRepository playerRepository,
                      PlayerItemRepository playerItemRepository,
                      ItemRepository itemRepository,
                      NpcDialogRepository npcDialogRepository,
                      ObjectMapper objectMapper) {
        this.npcRepository = npcRepository;
        this.shopItemRepository = shopItemRepository;
        this.warpDestinationRepository = warpDestinationRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemRepository = itemRepository;
        this.npcDialogRepository = npcDialogRepository;
        this.objectMapper = objectMapper;
    }

    public List<NpcResponseDTO> getNpcsForMap(String mapName) {
        return npcRepository.findByMapName(mapName).stream()
                .map(npc -> {
                    List<String> destinations = "WARP".equals(npc.getType().name())
                            ? warpDestinationRepository.findByNpcId(npc.getId()).stream()
                                    .map(NpcWarpDestinationEntity::getMapName)
                                    .toList()
                            : List.of();
                    return new NpcResponseDTO(
                            npc.getId(), npc.getName(), npc.getType().name(),
                            npc.getX(), npc.getY(), npc.getSpriteRef(), npc.getSpriteUrl(),
                            destinations);
                })
                .toList();
    }

    public NpcShopResponseDTO getShop(Long npcId) {
        NpcEntity npc = findNpc(npcId);
        if (npc.getType() != NpcType.SHOP) {
            throw new GameException("Este NPC não é uma loja");
        }
        List<NpcShopItemEntity> items = shopItemRepository.findByNpcId(npcId);
        // Fallback: navi_ NPCs have type=SHOP but items are on the shop_ NPC at same coords
        if (items.isEmpty()) {
            List<NpcEntity> nearby = npcRepository.findShopNpcsByProximity(
                    npc.getMapName(), npc.getX(), npc.getY(), 5);
            if (!nearby.isEmpty()) {
                items = shopItemRepository.findByNpcId(nearby.get(0).getId());
            }
        }
        List<NpcShopItemEntity> finalItems = items;
        List<NpcShopResponseDTO.ShopItemDTO> dtos = finalItems.stream()
                .map(si -> {
                    int price = si.getPrice() == -1 ? resolveItemPrice(si.getItemId()) : si.getPrice();
                    String imgUrl = itemRepository.findById(si.getItemId())
                            .map(ItemEntity::getImgUrl)
                            .orElse(null);
                    return new NpcShopResponseDTO.ShopItemDTO(si.getItemId(), resolveItemName(si.getItemId()), price, imgUrl);
                })
                .toList();
        return new NpcShopResponseDTO(npc.getName(), dtos);
    }

    @Transactional
    public NpcBuyResponseDTO buyFromNpc(Long npcId, Long playerId, Long itemId, int amount) {
        NpcEntity npc = findNpc(npcId);
        if (npc.getType() != NpcType.SHOP) {
            throw new GameException("Este NPC não é uma loja");
        }
        // Fallback: navi_ NPCs have type=SHOP but items are on the shop_ NPC at same coords
        Long shopNpcId = npcId;
        if (shopItemRepository.findByNpcIdAndItemId(npcId, itemId).isEmpty()) {
            List<NpcEntity> nearby = npcRepository.findShopNpcsByProximity(
                    npc.getMapName(), npc.getX(), npc.getY(), 5);
            if (!nearby.isEmpty()) shopNpcId = nearby.get(0).getId();
        }
        final Long resolvedShopNpcId = shopNpcId;
        NpcShopItemEntity shopItem = shopItemRepository.findByNpcIdAndItemId(resolvedShopNpcId, itemId)
                .orElseThrow(() -> new GameException("Item não disponível nesta loja"));

        PlayerEntity player = findPlayer(playerId);
        int price = shopItem.getPrice() == -1 ? resolveItemPrice(itemId) : shopItem.getPrice();
        long total = (long) price * amount;

        if (player.getZenny() == null || player.getZenny() < total) {
            throw new GameException("Zenny insuficiente");
        }
        player.setZenny(player.getZenny() - total);
        playerRepository.save(player);

        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(playerId, itemId);
        if (!existing.isEmpty()) {
            PlayerItemEntity stack = existing.get(0);
            stack.setAmount(stack.getAmount() + amount);
            playerItemRepository.save(stack);
        } else {
            ItemEntity item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new GameException("Item não encontrado: " + itemId));
            PlayerItemEntity pi = new PlayerItemEntity();
            pi.setPlayer(player);
            pi.setItem(item);
            pi.setAmount(amount);
            pi.setEquipped(false);
            playerItemRepository.save(pi);
        }

        return new NpcBuyResponseDTO("Compra realizada com sucesso", resolveItemName(itemId), player.getZenny());
    }

    @Transactional
    public NpcHealResponseDTO heal(Long npcId, Long playerId) {
        NpcEntity npc = findNpc(npcId);
        if (npc.getType() != NpcType.HEAL) {
            throw new GameException("Este NPC não oferece cura");
        }
        PlayerEntity player = findPlayer(playerId);
        player.setHpCurrent(player.getHpMax());
        player.setSpCurrent(player.getSpMax());
        playerRepository.save(player);
        return new NpcHealResponseDTO("HP e SP restaurados!", player.getHpCurrent(), player.getSpCurrent());
    }

    @Transactional
    public NpcWarpResponseDTO warp(Long npcId, Long playerId, String destination) {
        NpcEntity npc = findNpc(npcId);
        if (npc.getType() != NpcType.WARP) {
            throw new GameException("Este NPC não é um portal de warp");
        }
        NpcWarpDestinationEntity dest = warpDestinationRepository.findByNpcIdAndMapName(npcId, destination)
                .orElseThrow(() -> new GameException("Destino não disponível neste warp: " + destination));

        PlayerEntity player = findPlayer(playerId);
        player.setMapName(dest.getMapName());
        player.setCoordX(dest.getX());
        player.setCoordY(dest.getY());
        playerRepository.save(player);

        return new NpcWarpResponseDTO(dest.getMapName(), dest.getX(), dest.getY());
    }

    private NpcEntity findNpc(Long npcId) {
        return npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("NPC não encontrado: " + npcId));
    }

    private PlayerEntity findPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player não encontrado: " + playerId));
    }

    private int resolveItemPrice(Long itemId) {
        return itemRepository.findById(itemId)
                .map(item -> item.getPrice() != null ? item.getPrice() : 0)
                .orElse(0);
    }

    private String resolveItemName(Long itemId) {
        return itemRepository.findById(itemId)
                .map(ItemEntity::getName)
                .orElse("Unknown");
    }

    public NpcDialogTreeDTO getDialog(Long npcId) {
        NpcEntity npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("NPC não encontrado: " + npcId));

        Optional<NpcDialogEntity> dialogEntry = npcDialogRepository.findByNpcId(npcId);
        if (dialogEntry.isPresent()) {
            try {
                List<NpcDialogNode> nodes = objectMapper.readValue(
                        dialogEntry.get().getNodes(),
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, NpcDialogNode.class));
                return new NpcDialogTreeDTO(nodes);
            } catch (JsonProcessingException e) {
                log.warn("Erro ao desserializar nodes NPC id={}, usando fallback legado: {}", npcId, e.getMessage());
            }
        }
        return buildLegacyTree(npc);
    }

    /** Fallback: constrói árvore simples a partir do campo legado npcs.dialog. */
    private NpcDialogTreeDTO buildLegacyTree(NpcEntity npc) {
        String legacy = npc.getDialog();
        if (legacy == null || legacy.isBlank()) {
            return new NpcDialogTreeDTO(List.of());
        }
        String[] lines = legacy.split("\n");
        List<String> texts = new ArrayList<>();
        String speaker = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            if (speaker == null && trimmed.matches("^\\[.+]$")) {
                speaker = trimmed.substring(1, trimmed.length() - 1);
            } else {
                texts.add(trimmed);
            }
        }
        if (texts.isEmpty()) {
            return new NpcDialogTreeDTO(List.of());
        }
        return new NpcDialogTreeDTO(List.of(
                new NpcDialogNode.DialogNode(speaker, List.copyOf(texts), 1),
                new NpcDialogNode.ActionNode("close", null)
        ));
    }
}
