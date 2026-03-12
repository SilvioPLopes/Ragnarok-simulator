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

    public com.ragnarok.domain.model.Item criarItemDeTeste(Long id, String name, int attack) {
        ItemEntity entity = new ItemEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setAttack(attack);
        entity.setDefense(0);
        entity.setSlots(2);
        entity.setWeight(10);
        entity.setPrice(100);
        entity.setType(com.ragnarok.domain.model.ItemType.WEAPON);
        entity.setEquipSlot(com.ragnarok.domain.model.EquipSlot.HAND_R);
        itemRepository.save(entity);
        return itemMapper.toDomain(entity);
    }

    public void darItemAoJogador(Long playerId, long itemId, int qty) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        ItemEntity item = itemRepository.findById(itemId).orElseThrow();
        PlayerItemEntity playerItem = new PlayerItemEntity();
        playerItem.setPlayer(player);
        playerItem.setItem(item);
        playerItem.setAmount(qty);
        playerItem.setEquipped(false);
        playerItemRepository.save(playerItem);
    }

    public String equiparItem(Long playerId, UUID playerItemId) {
        PlayerItemEntity playerItem = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item não encontrado no inventário."));
        if (!playerItem.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("Tentativa de equipar item de outro jogador!");
        }
        ItemType tipo = playerItem.getItem().getType();
        if (tipo != ItemType.WEAPON && tipo != ItemType.ARMOR) {
            throw new IllegalArgumentException("Este item não é um equipamento.");
        }
        return gerenciarEquipamento(playerItem);
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
        var stats = itemEntity.getItem().getStats();
        Integer efeitoHp = stats.getEfeito();
        Integer efeitoSp = stats.getBonusSP();

        boolean temEfeitoHp = efeitoHp != null && efeitoHp > 0;
        boolean temEfeitoSp = efeitoSp != null && efeitoSp > 0;

        if (!temEfeitoHp && !temEfeitoSp) {
            return "Este item não tem efeito ao ser usado.";
        }

        int hpAtual = player.getHpCurrent() != null ? player.getHpCurrent() : 0;
        int hpMax = player.getHpMax() != null ? player.getHpMax() : 100;
        int spAtual = player.getSpCurrent() != null ? player.getSpCurrent() : 0;
        int spMax = player.getSpMax() != null ? player.getSpMax() : 40;

        boolean hpCheio = hpAtual >= hpMax;
        boolean spCheio = spAtual >= spMax;

        if ((temEfeitoHp && hpCheio && !temEfeitoSp) || (!temEfeitoHp && temEfeitoSp && spCheio)) {
            return temEfeitoHp ? "Seu HP já está cheio!" : "Seu SP já está cheio!";
        }
        if (temEfeitoHp && temEfeitoSp && hpCheio && spCheio) {
            return "HP e SP já estão cheios!";
        }

        int hpCurado = 0;
        int spCurado = 0;

        if (temEfeitoHp && !hpCheio) {
            int novoHp = Math.min(hpMax, hpAtual + efeitoHp);
            hpCurado = novoHp - hpAtual;
            player.setHpCurrent(novoHp);
        }
        if (temEfeitoSp && !spCheio) {
            int novoSp = Math.min(spMax, spAtual + efeitoSp);
            spCurado = novoSp - spAtual;
            player.setSpCurrent(novoSp);
        }

        playerRepository.save(player);

        if (itemEntity.getAmount() > 1) {
            itemEntity.setAmount(itemEntity.getAmount() - 1);
            playerItemRepository.save(itemEntity);
        } else {
            playerItemRepository.delete(itemEntity);
        }

        String nome = itemEntity.getItem().getName();
        if (hpCurado > 0 && spCurado > 0) {
            return String.format("Você usou %s e recuperou %d de HP e %d de SP.", nome, hpCurado, spCurado);
        } else if (hpCurado > 0) {
            return String.format("Você usou %s e recuperou %d de HP.", nome, hpCurado);
        } else {
            return String.format("Você usou %s e recuperou %d de SP.", nome, spCurado);
        }
    }
}