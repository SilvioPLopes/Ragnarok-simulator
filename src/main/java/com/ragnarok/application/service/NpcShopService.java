package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NpcShopService {

    private static final Logger log = LoggerFactory.getLogger(NpcShopService.class);

    private final ItemRepository itemRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;

    public NpcShopService(ItemRepository itemRepository,
                          PlayerRepository playerRepository,
                          PlayerItemRepository playerItemRepository) {
        this.itemRepository = itemRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
    }

    public List<ItemEntity> listItems() {
        return itemRepository.findAll();
    }

    @Transactional
    public void buy(Long playerId, Long itemId, int quantity) {
        log.info("[NpcShop] Compra iniciada: playerId={}, itemId={}, quantidade={}", playerId, itemId, quantity);
        ItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado: " + itemId));
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player nao encontrado"));

        long total = (long) item.getPrice() * quantity;
        if (player.getZenny() < total) {
            log.warn("[NpcShop] Zenny insuficiente: playerId={}, necessario={}, disponivel={}", playerId, total, player.getZenny());
            throw new IllegalStateException("Zenny insuficiente");
        }
        player.setZenny(player.getZenny() - total);
        playerRepository.save(player);

        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(playerId, itemId);
        if (!existing.isEmpty()) {
            PlayerItemEntity stack = existing.get(0);
            stack.setAmount(stack.getAmount() + quantity);
            playerItemRepository.save(stack);
        } else {
            PlayerItemEntity pi = new PlayerItemEntity();
            pi.setPlayer(player);
            pi.setItem(item);
            pi.setAmount(quantity);
            pi.setEquipped(false);
            playerItemRepository.save(pi);
        }
        log.info("[NpcShop] Compra concluida: playerId={}, itemId={}, qty={}, zenny_gasto={}", playerId, itemId, quantity, total);
    }

    @Transactional
    public List<PlayerItemEntity> sell(Long playerId, UUID playerItemId, int quantity) {
        log.info("[NpcShop] Venda iniciada: playerId={}, playerItemId={}, quantidade={}", playerId, playerItemId, quantity);
        PlayerItemEntity pi = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado no inventario"));
        if (!pi.getPlayer().getId().equals(playerId)) {
            log.warn("[NpcShop] Tentativa de vender item de outro jogador: playerId={}, itemOwner={}", playerId, pi.getPlayer().getId());
            throw new IllegalArgumentException("Item nao pertence a este jogador");
        }
        if (pi.getAmount() < quantity) {
            log.warn("[NpcShop] Quantidade insuficiente para venda: playerId={}, disponivel={}, pedido={}", playerId, pi.getAmount(), quantity);
            throw new IllegalStateException("Quantidade insuficiente no inventario");
        }
        long credit = (long) pi.getItem().getPrice() * quantity / 2;
        PlayerEntity player = pi.getPlayer();
        player.setZenny(player.getZenny() + credit);

        if (pi.getAmount() == quantity) {
            // Remove via collection so orphanRemoval handles the DELETE cleanly,
            // avoiding ObjectDeletedException caused by cascade + explicit delete conflict.
            player.getInventory().remove(pi);
        } else {
            pi.setAmount(pi.getAmount() - quantity);
        }
        playerRepository.save(player);
        log.info("[NpcShop] Venda concluida: playerId={}, credit_recebido={}", playerId, credit);
        return playerItemRepository.findByPlayerId(playerId);
    }
}
