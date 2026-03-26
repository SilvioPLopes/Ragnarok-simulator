package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.domain.model.EffectResult;
import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.model.ItemStats;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.application.service.ScriptInterpreter;
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
    private final ScriptInterpreter scriptInterpreter;

    public ItemService(ItemRepository itemRepository,
                       PlayerRepository playerRepository,
                       PlayerItemRepository playerItemRepository,
                       ItemMapper itemMapper,
                       ScriptInterpreter scriptInterpreter) {
        this.itemRepository = itemRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemMapper = itemMapper;
        this.scriptInterpreter = scriptInterpreter;
    }

    public void darItemAoJogador(Long playerId, long itemId, int qty) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow(() -> new GameException("Player not found: " + playerId));
        ItemEntity item = itemRepository.findById(itemId).orElseThrow(() -> new GameException("Item not found: " + itemId));
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
            throw new GameException("Tentativa de equipar item de outro jogador!");
        }
        ItemType tipo = playerItem.getItem().getType();
        if (tipo != ItemType.WEAPON && tipo != ItemType.ARMOR) {
            throw new GameException("Este item não é um equipamento.");
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
            throw new GameException("Tentativa de equipar item de outro jogador!");
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
        ItemEntity item = itemEntity.getItem();
        String nome = item.getName();

        // Tenta interpretar via script rAthena
        if (item.getScript() != null) {
            EffectResult result = scriptInterpreter.interpret(item.getScript());
            if (!result.supported()) {
                return "Este item não pode ser usado ainda.";
            }
            int hpMax = player.getHpMax() != null ? player.getHpMax() : 100;
            int spMax = player.getSpMax() != null ? player.getSpMax() : 40;
            int hpHeal = result.isPercent() ? hpMax * result.hpHeal() / 100 : result.hpHeal();
            int spHeal = result.isPercent() ? spMax * result.spHeal() / 100 : result.spHeal();
            return aplicarCura(player, itemEntity, hpHeal, spHeal, nome);
        }

        // Fallback legado: usa campo efeito (UPDATEs manuais anteriores)
        var stats = item.getStats();
        Integer efeitoHp = stats.getEfeito();
        Integer efeitoSp = stats.getBonusSP();

        boolean temEfeitoHp = efeitoHp != null && efeitoHp > 0;
        boolean temEfeitoSp = efeitoSp != null && efeitoSp > 0;

        if (!temEfeitoHp && !temEfeitoSp) {
            return "Este item não tem efeito.";
        }

        return aplicarCura(player, itemEntity, temEfeitoHp ? efeitoHp : 0, temEfeitoSp ? efeitoSp : 0, nome);
    }

    public List<PlayerItemEntity> listarInventario(Long playerId) {
        return playerItemRepository.findByPlayerId(playerId);
    }

    private String aplicarCura(PlayerEntity player, PlayerItemEntity itemEntity, int hpHeal, int spHeal, String nome) {
        int hpAtual = player.getHpCurrent() != null ? player.getHpCurrent() : 0;
        int hpMax   = player.getHpMax()     != null ? player.getHpMax()     : 100;
        int spAtual = player.getSpCurrent() != null ? player.getSpCurrent() : 0;
        int spMax   = player.getSpMax()     != null ? player.getSpMax()     : 40;

        boolean hpCheio = hpAtual >= hpMax;
        boolean spCheio = spAtual >= spMax;

        if ((hpHeal > 0 && hpCheio && spHeal == 0) || (spHeal > 0 && spCheio && hpHeal == 0)) {
            return hpHeal > 0 ? "Seu HP já está cheio!" : "Seu SP já está cheio!";
        }
        if (hpHeal > 0 && spHeal > 0 && hpCheio && spCheio) {
            return "HP e SP já estão cheios!";
        }

        int hpCurado = 0;
        int spCurado = 0;

        if (hpHeal > 0 && !hpCheio) {
            int novoHp = Math.min(hpMax, hpAtual + hpHeal);
            hpCurado = novoHp - hpAtual;
            player.setHpCurrent(novoHp);
        }
        if (spHeal > 0 && !spCheio) {
            int novoSp = Math.min(spMax, spAtual + spHeal);
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

        if (hpCurado > 0 && spCurado > 0) {
            return String.format("Você usou %s e recuperou %d de HP e %d de SP.", nome, hpCurado, spCurado);
        } else if (hpCurado > 0) {
            return String.format("Você usou %s e recuperou %d de HP.", nome, hpCurado);
        } else {
            return String.format("Você usou %s e recuperou %d de SP.", nome, spCurado);
        }
    }
}