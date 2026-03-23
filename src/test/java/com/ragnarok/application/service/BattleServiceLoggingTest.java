package com.ragnarok.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleServiceLoggingTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private MonsterRepository monsterRepository;
    @Mock private PlayerItemRepository playerItemRepository;
    @Mock private PlayerMapper playerMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private MonsterMapper monsterMapper;
    @Mock private BattleEngine battleEngine;
    @Mock private LevelingService levelingService;
    @Mock private WeaponSizeService weaponSizeService;

    @InjectMocks
    private BattleService battleService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachAppender() {
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(BattleService.class);
        serviceLogger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(BattleService.class);
        serviceLogger.detachAppender(logAppender);
        serviceLogger.setLevel(null);
    }

    @Test
    @DisplayName("Logging: deve emitir WARN quando player morto tenta atacar")
    void realizarAtaque_deveEmitirWarn_quandoPlayerEstaMorto() {
        PlayerEntity playerMorto = new PlayerEntity();
        playerMorto.setId(1L);
        playerMorto.setHpCurrent(0);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerMorto));
        when(monsterRepository.findById(10L)).thenReturn(Optional.of(new MonsterEntity()));

        assertThrows(PlayerDeadException.class,
                () -> battleService.realizarAtaque(1L, 10L));

        List<ILoggingEvent> warnLogs = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();

        assertEquals(1, warnLogs.size(), "Deve emitir exatamente 1 WARN");
        assertTrue(warnLogs.get(0).getFormattedMessage().contains("morto"),
                "Mensagem deve indicar que o player está morto");
    }

    @Test
    @DisplayName("Logging: deve emitir INFO quando player derrota monstro")
    void realizarAtaque_deveEmitirInfo_quandoMonsterMorre() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(1); // HP baixo -> morre no próximo golpe

        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(1);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(10L)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(any(), any())).thenReturn(999); // Dano letal
        when(battleEngine.applyWeaponSizeModifier(anyInt(), anyInt())).thenReturn(999);
        when(weaponSizeService.getModifier(any(), any())).thenReturn(100);
        when(battleEngine.calculateLoot(any())).thenReturn(List.of());
        when(levelingService.processarExperiencia(any(), anyLong(), anyLong())).thenReturn("EXP: +5\n");
        lenient().when(playerMapper.serializeBuffs(any())).thenReturn("[]");
        when(playerRepository.save(any())).thenReturn(playerEntity);

        battleService.realizarAtaque(1L, 10L);

        List<ILoggingEvent> infoLogs = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .toList();

        assertFalse(infoLogs.isEmpty(), "Deve emitir pelo menos 1 INFO ao matar monstro");
        assertTrue(infoLogs.stream().anyMatch(e -> e.getFormattedMessage().contains("Poring")),
                "Log de vitória deve mencionar o nome do monstro");
    }

    // --- helpers ---

    private PlayerEntity makePlayerEntity(int hp) {
        PlayerEntity e = new PlayerEntity();
        e.setId(1L);
        e.setName("Hero");
        e.setJobClass("NOVICE");
        e.setBaseLevel(1); e.setJobLevel(1);
        e.setBaseExp(0L); e.setJobExp(0L);
        e.setStatPoints(0); e.setSkillPoints(0);
        e.setHpCurrent(hp); e.setHpMax(100);
        e.setSpCurrent(40); e.setSpMax(40);
        e.setStr(5); e.setAgi(1); e.setVit(1);
        e.setIntelligence(1); e.setDex(1); e.setLuk(1);
        return e;
    }

    private MonsterEntity makeMonsterEntity(int hp) {
        MonsterEntity e = new MonsterEntity();
        e.setId(10L);
        e.setName("Poring");
        e.setHp(hp);
        e.setAttack(10);
        e.setDef(0);
        e.setBaseExp(5);
        e.setJobExp(3);
        return e;
    }

    private Player makeDomainPlayer(int hp) {
        Player p = new Player();
        p.setId(1L);
        p.setName("Hero");
        p.setJobClass("NOVICE");
        p.setBaseLevel(1); p.setJobLevel(1);
        p.setBaseExp(0L); p.setJobExp(0L);
        p.setStatPoints(0); p.setSkillPoints(0);
        p.setHpCurrent(hp); p.setSpCurrent(40);
        p.setStats(new PlayerStats(5, 1, 1, 1, 1, 1, 100, 40));
        p.setInventory(new ArrayList<>());
        return p;
    }

    private Monster makeDomainMonster(int hp) {
        Monster m = new Monster();
        m.setId(10L);
        m.setName("Poring");
        MainStats stats = new MainStats();
        stats.setHp(hp); stats.setAttack(10); stats.setDef(0);
        m.setStats(stats);
        m.setBaseExp(5); m.setJobExp(3);
        m.setDrops(new ArrayList<>());
        return m;
    }
}
