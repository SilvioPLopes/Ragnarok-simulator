package com.ragnarok.application.service;

import com.ragnarok.domain.model.*;
import com.ragnarok.domain.model.Monster;
import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BattleService {

    private final PlayerRepository playerRepository;
    private final MonsterRepository monsterRepository;
    private final PlayerMapper playerMapper;
    private final PlayerItemRepository playerItemRepository; // Necessário para salvar o loot
    private final ItemMapper itemMapper;
    private final MonsterMapper monsterMapper;
    private final BattleEngine battleEngine;
    private final LevelingService levelingService;

    public BattleService(PlayerRepository playerRepository,
                         MonsterRepository monsterRepository,
                         PlayerItemRepository playerItemRepository,
                         PlayerMapper playerMapper,
                         ItemMapper itemMapper,
                         MonsterMapper monsterMapper,
                         BattleEngine battleEngine,
                         LevelingService levelingService) {
        this.playerRepository = playerRepository;
        this.monsterRepository = monsterRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemMapper = itemMapper;
        this.playerMapper = playerMapper;
        this.monsterMapper = monsterMapper;
        this.battleEngine = battleEngine;
        this.levelingService = levelingService;
    }

    @Transactional
    public String realizarAtaque(Long playerId, Long monsterId) {
        // 1. Carregar Dados
        PlayerEntity playerEntity = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                .orElseThrow(() -> new IllegalArgumentException("Monster not found"));

        if (playerEntity.getHpCurrent() != null && playerEntity.getHpCurrent() <= 0) {
            throw new IllegalStateException("O jogador está morto e não pode realizar ações de combate.");
        }

        Player player = playerMapper.toDomain(playerEntity);
        Monster monster = monsterMapper.toDomain(monsterEntity);

        // 2. Calcular Dano
        int damage = battleEngine.calculateDamage(player, monster);

        // 3. Aplicar Dano no Banco (Turno do Jogador)
        int newHp = Math.max(0, monsterEntity.getHp() - damage);
        monsterEntity.setHp(newHp);
        monsterRepository.save(monsterEntity);

        // 4. Verificar Morte
        if (newHp <= 0) {
            return processarMorteMonstro(playerEntity, monster);
        }


        // 5. Contra-ataque do Monstro
        int monsterDamage = battleEngine.calculateMonsterDamage(monster, player);
        int playerNewHp = Math.max(0, playerEntity.getHpCurrent() - monsterDamage);
        playerEntity.setHpCurrent(playerNewHp);
        playerRepository.save(playerEntity);

        if (playerNewHp <= 0) {
            return String.format("FATAL: Você causou %d de dano, mas o %s contra-atacou com %d e você morreu.", damage, monster.getName(), monsterDamage);
        }

        String arma = identificarArma(player);
        return String.format("ATAQUE: Voce causou %d de dano no %s com %s. (HP restante: %d)\n  >> %s contra-atacou e causou %d de dano em voce!",
                damage, monster.getName(), arma, newHp, monster.getName(), monsterDamage);
    }

    private String processarMorteMonstro(PlayerEntity playerEntity, Monster monsterDomain) {
        StringBuilder log = new StringBuilder();
        log.append("\n🌟 VITÓRIA! O ").append(monsterDomain.getName()).append(" foi derrotado.\n");

        // 1. Processar Drops (Código existente)
        List<Item> loots = battleEngine.calculateLoot(monsterDomain);
        if (loots.isEmpty()) {
            log.append("Loot: Nenhum item caiu.\n");
        } else {
            log.append("Loot: ");
            for (Item itemDomain : loots) {
                ItemEntity itemEntity = itemMapper.toEntity(itemDomain);
                java.util.List<PlayerItemEntity> existing =
                        playerItemRepository.findByPlayerIdAndItemId(playerEntity.getId(), itemEntity.getId());
                if (!existing.isEmpty()) {
                    // Mescla todos os duplicados no primeiro slot e remove os demais
                    PlayerItemEntity stack = existing.get(0);
                    int total = existing.stream().mapToInt(e -> e.getAmount() != null ? e.getAmount() : 1).sum();
                    stack.setAmount(total + 1);
                    playerItemRepository.save(stack);
                    if (existing.size() > 1) {
                        playerItemRepository.deleteAll(existing.subList(1, existing.size()));
                    }
                } else {
                    PlayerItemEntity newItem = new PlayerItemEntity();
                    newItem.setPlayer(playerEntity);
                    newItem.setItem(itemEntity);
                    newItem.setAmount(1);
                    newItem.setRefineLevel(0);
                    newItem.setEquipped(false);
                    playerItemRepository.save(newItem);
                }
                log.append("[").append(itemDomain.getName()).append("] ");
            }
            log.append("\n");
        }

        // 2. PROCESSAR EXPERIÊNCIA (NOVO)
        // Converte Entity para Domain para aplicar lógica
        Player playerDomain = playerMapper.toDomain(playerEntity);

        // Garante que não é null (safe check)
        long baseExpGain = monsterDomain.getBaseExp() != null ? monsterDomain.getBaseExp() : 0;
        long jobExpGain = monsterDomain.getJobExp() != null ? monsterDomain.getJobExp() : 0;

        // Chama o Domain Service para calcular Level Up
        String levelLog = levelingService.processarExperiencia(playerDomain, baseExpGain, jobExpGain);
        log.append(levelLog);

        // 3. Atualiza Entity com os novos dados do Domain (Level, Exp, Pontos)
        playerEntity.setBaseLevel(playerDomain.getBaseLevel());
        playerEntity.setJobLevel(playerDomain.getJobLevel());
        playerEntity.setBaseExp(playerDomain.getBaseExp());
        playerEntity.setJobExp(playerDomain.getJobExp());
        playerEntity.setStatPoints(playerDomain.getStatPoints());
        playerEntity.setSkillPoints(playerDomain.getSkillPoints());

        // Se houve level up e curou, atualiza HP também
        playerEntity.setHpCurrent(playerDomain.getHpCurrent());
        playerEntity.setSpCurrent(playerDomain.getSpCurrent());

        // Persiste todos os dados de XP, level e pontos acumulados
        playerRepository.save(playerEntity);

        return log.toString();
    }

    private String identificarArma(Player p) {
        return p.getInventory().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsEquipped()))
                .findFirst()
                .map(i -> i.getName() + " (ATK " + i.getItemDefinition().getStats().getAttack() + ")")
                .orElse("Punhos Nus");
    }
}