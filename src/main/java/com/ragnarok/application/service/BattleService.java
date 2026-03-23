package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.model.Monster;
import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.domain.exception.PlayerDeadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    private final PlayerRepository playerRepository;
    private final MonsterRepository monsterRepository;
    private final PlayerMapper playerMapper;
    private final MonsterMapper monsterMapper;
    private final BattleEngine battleEngine;
    private final WeaponSizeService weaponSizeService;
    private final ApplicationEventPublisher eventPublisher;

    public BattleService(PlayerRepository playerRepository,
                         MonsterRepository monsterRepository,
                         PlayerMapper playerMapper,
                         MonsterMapper monsterMapper,
                         BattleEngine battleEngine,
                         WeaponSizeService weaponSizeService,
                         ApplicationEventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.monsterRepository = monsterRepository;
        this.playerMapper = playerMapper;
        this.monsterMapper = monsterMapper;
        this.battleEngine = battleEngine;
        this.weaponSizeService = weaponSizeService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String realizarAtaque(Long playerId, Long monsterId) {
        // 1. Carregar Dados
        PlayerEntity playerEntity = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                .orElseThrow(() -> new IllegalArgumentException("Monster not found"));

        if (playerEntity.getHpCurrent() != null && playerEntity.getHpCurrent() <= 0) {
            log.warn("Tentativa de ataque bloqueada: player {} está morto.", playerId);
            throw new PlayerDeadException();
        }

        Player player = playerMapper.toDomain(playerEntity);
        Monster monster = monsterMapper.toDomain(monsterEntity);

        // 2. Calcular Dano base
        int damage = battleEngine.calculateDamage(player, monster);

        // 2a. Aplicar modificador de tamanho da arma
        WeaponType weaponType = player.getEquipments().stream()
                .map(i -> i.getItemDefinition() != null ? i.getItemDefinition().getWeaponType() : null)
                .filter(wt -> wt != null && wt != WeaponType.NONE)
                .findFirst()
                .orElse(WeaponType.NONE);
        int sizeModPct = weaponSizeService.getModifier(weaponType, monster.getSize());
        damage = battleEngine.applyWeaponSizeModifier(damage, sizeModPct);

        // 3. Aplicar Dano no Banco (Turno do Jogador)
        int newHp = Math.max(0, monsterEntity.getHp() - damage);
        monsterEntity.setHp(newHp);
        monsterRepository.save(monsterEntity);

        // 4. Verificar Morte
        if (newHp <= 0) {
            log.info("Player {} derrotou {}.", playerId, monster.getName());
            List<Item> loot = battleEngine.calculateLoot(monster);
            long baseExp = monster.getBaseExp() != null ? monster.getBaseExp() : 0L;
            long jobExp  = monster.getJobExp()  != null ? monster.getJobExp()  : 0L;
            eventPublisher.publishEvent(new MonsterKilledEvent(playerId, monsterId, loot, baseExp, jobExp));
            String dropLog = loot.isEmpty() ? "" :
                    "\nDrop: " + loot.stream().map(Item::getName).collect(Collectors.joining(", "));
            return "\uD83C\uDF1F VITÓRIA! O " + monster.getName() + " foi derrotado." + dropLog;
        }


        // 5. Contra-ataque do Monstro
        int monsterDamage = battleEngine.calculateMonsterDamage(monster, player);
        int playerNewHp = Math.max(0, playerEntity.getHpCurrent() - monsterDamage);
        playerEntity.setHpCurrent(playerNewHp);

        // 6. Decrementar buffs ativos do player (1 turno = 1 ação)
        player.decrementarBuffs();
        playerEntity.setActiveBuffsJson(playerMapper.serializeBuffs(player));

        playerRepository.save(playerEntity);

        if (playerNewHp <= 0) {
            log.info("Player {} morreu para {}.", playerId, monster.getName());
            eventPublisher.publishEvent(new PlayerDiedEvent(playerId));
            return String.format("FATAL: Você causou %d de dano, mas o %s contra-atacou com %d e você morreu.", damage, monster.getName(), monsterDamage);
        }

        String arma = identificarArma(player);
        return String.format("ATAQUE: Voce causou %d de dano no %s com %s. (HP restante: %d)\n  >> %s contra-atacou e causou %d de dano em voce!",
                damage, monster.getName(), arma, newHp, monster.getName(), monsterDamage);
    }

    private String identificarArma(Player p) {
        return p.getInventory().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsEquipped()))
                .findFirst()
                .map(i -> i.getName() + " (ATK " + i.getItemDefinition().getStats().getAttack() + ")")
                .orElse("Punhos Nus");
    }
}