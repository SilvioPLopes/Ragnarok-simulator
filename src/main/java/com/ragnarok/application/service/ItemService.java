package com.ragnarok.application.service;

import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.model.ItemStats;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ItemService {

    private final ItemRepository itemRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final ItemMapper itemMapper;

    public ItemService(ItemRepository itemRepository,
                       PlayerRepository playerRepository,
                       PlayerItemRepository playerItemRepository,
                       ItemMapper itemMapper) {
        this.itemRepository = itemRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemMapper = itemMapper;
    }

    public String usarItem(UUID playerItemId) {
        PlayerItemEntity playerItem = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item não encontrado no inventário."));

        ItemType tipo = playerItem.getItem().getType();

        if (tipo == ItemType.WEAPON || tipo == ItemType.ARMOR) {
            return gerenciarEquipamento(playerItem); // Unificado
        } else if (tipo == ItemType.CONSUMABLE) {
            return consumirItem(playerItem);
        } else {
            return "Este item não pode ser usado diretamente.";
        }
    }

    private String gerenciarEquipamento(PlayerItemEntity itemAlvo) {
        if (itemAlvo.getEquipped()) {
            itemAlvo.setEquipped(false);
            playerItemRepository.save(itemAlvo);
            return "Item desequipado: " + itemAlvo.getItem().getName();
        }

        if (!itemAlvo.getPlayer().getId().equals(itemAlvo.getPlayer().getId())) {
            throw new IllegalStateException("Tentativa de equipar item de outro jogador!");
        }

        EquipSlot slotAlvo = itemAlvo.getItem().getEquipSlot();

        if (slotAlvo != null && slotAlvo != EquipSlot.NONE) {
            List<PlayerItemEntity> equipados = playerItemRepository.findByPlayerIdAndEquippedTrue(itemAlvo.getPlayer().getId());

            for (PlayerItemEntity itemAtual : equipados) {
                // Se encontrar item no mesmo slot, desequipa
                if (itemAtual.getItem().getEquipSlot() == slotAlvo) {
                    itemAtual.setEquipped(false);
                    playerItemRepository.save(itemAtual);
                }
            }
        }

        // 4. Equipa o novo
        itemAlvo.setEquipped(true);
        playerItemRepository.save(itemAlvo);
        return "Item equipado com sucesso.";
    }

    private String consumirItem(PlayerItemEntity itemEntity) {
        PlayerEntity player = itemEntity.getPlayer();
        Integer poderDeCura = itemEntity.getItem().getStats().getEfeito();

        if (poderDeCura == null || poderDeCura <= 0) {
            return "Este item não tem efeito ao ser usado.";
        }

        int hpAtual = player.getHpCurrent() != null ? player.getHpCurrent() : 0;
        int hpMax = player.getHpMax() != null ? player.getHpMax() : 100;

        if (hpAtual >= hpMax) {
            return "Seu HP já está cheio!";
        }

        int novoHp = Math.min(hpMax, hpAtual + poderDeCura);
        player.setHpCurrent(novoHp);
        playerRepository.save(player);

        if (itemEntity.getAmount() > 1) {
            itemEntity.setAmount(itemEntity.getAmount() - 1);
            playerItemRepository.save(itemEntity);
        } else {
            playerItemRepository.delete(itemEntity);
        }

        return String.format("Você usou %s e recuperou %d de HP.", itemEntity.getItem().getName(), poderDeCura);
    }
}