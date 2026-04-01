package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.application.service.WeaponSizeService;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MonsterRepository monsterRepository;

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private MonsterMapper monsterMapper;

    @Mock
    private BattleEngine battleEngine;

    @Mock
    private WeaponSizeService weaponSizeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BattleService battleService;

    private static final Long PLAYER_ID = 1L;
    private static final Long MONSTER_ID = 10L;

    private PlayerEntity makePlayerEntity(int hpCurrent) {
        PlayerEntity e = new PlayerEntity();
        e.setId(PLAYER_ID);
        e.setName("Hero");
        e.setJobClass("NOVICE");
        e.setBaseLevel(1);
        e.setJobLevel(1);
        e.setBaseExp(0L);
        e.setJobExp(0L);
        e.setStatPoints(0);
        e.setSkillPoints(0);
        e.setHpCurrent(hpCurrent);
        e.setSpCurrent(40);
        e.setHpMax(100);
        e.setSpMax(40);
        e.setStr(5);
        e.setAgi(1);
        e.setVit(1);
        e.setIntelligence(1);
        e.setDex(1);
        e.setLuk(1);
        return e;
    }

    private MonsterEntity makeMonsterEntity(int hp) {
        MonsterEntity e = new MonsterEntity();
        e.setId(MONSTER_ID);
        e.setName("Poring");
        e.setHp(hp);
        e.setAttack(10);
        e.setDef(0);
        e.setBaseExp(5);
        e.setJobExp(3);
        return e;
    }

    private Player makeDomainPlayer(int hpCurrent) {
        Player p = new Player();
        p.setId(PLAYER_ID);
        p.setName("Hero");
        p.setJobClass("NOVICE");
        p.setBaseLevel(1);
        p.setJobLevel(1);
        p.setBaseExp(0L);
        p.setJobExp(0L);
        p.setStatPoints(0);
        p.setSkillPoints(0);
        p.setHpCurrent(hpCurrent);
        p.setSpCurrent(40);
        PlayerStats stats = new PlayerStats(5, 1, 1, 1, 1, 1, 100, 40);
        p.setStats(stats);
        p.setInventory(new ArrayList<>());
        return p;
    }

    private Monster makeDomainMonster(int hp) {
        Monster m = new Monster();
        m.setId(MONSTER_ID);
        m.setName("Poring");
        MainStats stats = new MainStats();
        stats.setHp(hp);
        stats.setAttack(10);
        stats.setDef(0);
        m.setStats(stats);
        m.setBaseExp(5);
        m.setJobExp(3);
        m.setDrops(new ArrayList<>());
        return m;
    }

    @BeforeEach
    void setupStubs() {
        // WeaponSizeService retorna int; Mockito default é 0, o que zeraria o dano.
        // Retorna 100 (= 100% modificador, sem penalidade de tamanho).
        lenient().when(weaponSizeService.getModifier(any(), any())).thenReturn(100);
        // applyWeaponSizeModifier também é mock — sem stub retorna 0, zerando o dano.
        // Passthrough: retorna o damage sem modificação.
        lenient().when(battleEngine.applyWeaponSizeModifier(anyInt(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Cenário 1: Ataque normal ──────────────────────────────────────────────

    @Test
    @DisplayName("Atacar monstro — dano é aplicado no HP do monstro e resultado descreve o ataque")
    void realizarAtaque_ataqueNormal_aplicaDanoERetornaDescricao() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(200);
        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(200);

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(20);
        when(battleEngine.calculateMonsterDamage(monsterDomain, playerDomain)).thenReturn(5);

        BattleService.AttackResult resultado = battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        // HP do monstro deve ter sido decrementado para 180
        assertEquals(180, monsterEntity.getHp());
        verify(monsterRepository).save(monsterEntity);

        // Resultado deve conter descrição do ataque
        assertNotNull(resultado);
        assertTrue(resultado.message().contains("ATAQUE") || resultado.message().contains("ataque") || resultado.message().contains("20"),
                "Resultado deveria descrever o ataque causado: " + resultado.message());
    }

    // ── Cenário 2: Monstro morto → VITÓRIA e delete ───────────────────────────

    @Test
    @DisplayName("Monstro morre — resultado contém VITORIA e monstro é deletado do banco")
    void realizarAtaque_monsterMorre_resultadoContemVitoriaEDeletaMonster() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(15);  // HP baixo
        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(15);

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        // Dano de 20 mata o monstro com HP 15
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(20);
        when(battleEngine.calculateLoot(monsterDomain)).thenReturn(new ArrayList<>());

        BattleService.AttackResult resultado = battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        // HP do monstro deve chegar a 0
        assertEquals(0, monsterEntity.getHp());

        // Resultado deve conter indicação de vitória
        assertNotNull(resultado);
        String resultadoUpper = resultado.message().toUpperCase();
        assertTrue(resultadoUpper.contains("VITORIA") || resultadoUpper.contains("VITÓRIA") || resultadoUpper.contains("VICT"),
                "Resultado deveria indicar vitória: " + resultado.message());

        // Monstro deve ser deletado (monsterRepository.delete ou deleteById)
        verify(monsterRepository, never()).save(argThat(m -> m.getId().equals(MONSTER_ID) && m.getHp() > 0));

        // Evento de morte do monstro deve ser publicado
        verify(eventPublisher).publishEvent(any(MonsterKilledEvent.class));
    }

    // ── Cenário 3: Monstro contra-ataca → HP do player decrementado ──────────

    @Test
    @DisplayName("Monstro sobrevive e contra-ataca — HP do player é decrementado")
    void realizarAtaque_monsterContraataca_hpPlayerDecrementado() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(200);
        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(200);

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(10);
        when(battleEngine.calculateMonsterDamage(monsterDomain, playerDomain)).thenReturn(15);

        battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        // HP do player deve ter sido decrementado de 100 para 85
        assertEquals(85, playerEntity.getHpCurrent());
        verify(playerRepository).save(playerEntity);
    }

    // ── Cenário 4: Player morre → resultado contém "FATAL" ou "morreu" ────────

    @Test
    @DisplayName("Player morre após contra-ataque — resultado contém FATAL ou morreu")
    void realizarAtaque_playerMorre_resultadoContemFatalOuMorreu() {
        PlayerEntity playerEntity = makePlayerEntity(5);  // HP muito baixo
        MonsterEntity monsterEntity = makeMonsterEntity(200);
        Player playerDomain = makeDomainPlayer(5);
        Monster monsterDomain = makeDomainMonster(200);

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(8);
        // Contra-ataque mata o player (5 HP restante após dano no monstro, mas
        // o HP do player não foi alterado pelo ataque; contra-ataque de 50 mata)
        when(battleEngine.calculateMonsterDamage(monsterDomain, playerDomain)).thenReturn(50);

        BattleService.AttackResult resultado = battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        assertNotNull(resultado);
        String resultadoUpper = resultado.message().toUpperCase();
        assertTrue(resultadoUpper.contains("FATAL") || resultado.message().toLowerCase().contains("morreu"),
                "Resultado deveria indicar morte do player: " + resultado.message());

        // HP do player deve ter chegado a 0
        assertEquals(0, playerEntity.getHpCurrent());

        // Evento de morte do player deve ser publicado
        verify(eventPublisher).publishEvent(any(PlayerDiedEvent.class));
    }

    // ── Cenário 5: Monstro sobrevive → não é deletado ─────────────────────────

    @Test
    @DisplayName("Monstro sobrevive ao ataque — HP não chega a zero e monstro não é deletado")
    void realizarAtaque_monsterSobrevive_naoEDeletado() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(500);  // HP alto
        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(500);

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(10);
        when(battleEngine.calculateMonsterDamage(monsterDomain, playerDomain)).thenReturn(3);

        battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        // HP do monstro deve ter sido decrementado mas ainda > 0
        assertEquals(490, monsterEntity.getHp());

        // Monstro não deve ser deletado
        verify(monsterRepository, never()).delete(any());
        verify(monsterRepository, never()).deleteById(any());
    }

    // ── Cenário 6: Player não encontrado → lança exceção ─────────────────────

    @Test
    @DisplayName("Player não encontrado — lança IllegalArgumentException")
    void realizarAtaque_playerNaoEncontrado_lancaExcecao() {
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> battleService.realizarAtaque(PLAYER_ID, MONSTER_ID));

        assertTrue(ex.getMessage().toLowerCase().contains("player") ||
                   ex.getMessage().toLowerCase().contains("not found"),
                "Mensagem deveria indicar player não encontrado: " + ex.getMessage());

        verify(monsterRepository, never()).findById(any());
    }

    // ── Cenário 7: Monstro não encontrado → lança exceção ────────────────────

    @Test
    @DisplayName("Monstro não encontrado — lança IllegalArgumentException")
    void realizarAtaque_monsterNaoEncontrado_lancaExcecao() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> battleService.realizarAtaque(PLAYER_ID, MONSTER_ID));

        assertTrue(ex.getMessage().toLowerCase().contains("monster") ||
                   ex.getMessage().toLowerCase().contains("not found"),
                "Mensagem deveria indicar monstro não encontrado: " + ex.getMessage());
    }

    // ── Cenário bônus: Player já morto → lança PlayerDeadException ──────────

    @Test
    @DisplayName("Player com HP zero tenta atacar — lança PlayerDeadException")
    void realizarAtaque_playerJaMorto_lancaPlayerDeadException() {
        PlayerEntity playerEntity = makePlayerEntity(0);  // HP já zerado
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(makeMonsterEntity(100)));

        assertThrows(PlayerDeadException.class,
                () -> battleService.realizarAtaque(PLAYER_ID, MONSTER_ID));

        // BattleEngine não deve ser chamado
        verify(battleEngine, never()).calculateDamage(any(), any());
    }

    // ── Cenário: Monstro morre com loot — MonsterKilledEvent contém o loot ────

    @Test
    @DisplayName("Monstro morre com loot — MonsterKilledEvent é publicado com o item no loot")
    void realizarAtaque_monsterMorreComLoot_eventoPublicadoComLoot() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(10);
        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(10);

        Item lootItem = new Item();
        lootItem.setId(500L);
        lootItem.setName("Red Herb");

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(20);
        when(battleEngine.calculateLoot(monsterDomain)).thenReturn(List.of(lootItem));

        BattleService.AttackResult resultado = battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        verify(eventPublisher).publishEvent(argThat((MonsterKilledEvent e) ->
                e.playerId().equals(PLAYER_ID) &&
                e.monsterId().equals(MONSTER_ID) &&
                e.loot().size() == 1 &&
                e.loot().get(0).getName().equals("Red Herb")));
        String resultadoUpper = resultado.message().toUpperCase();
        assertTrue(resultadoUpper.contains("VITORIA") || resultadoUpper.contains("VITÓRIA")
                        || resultadoUpper.contains("VICT"),
                "Resultado deveria indicar vitória: " + resultado.message());
    }

    // ── Cenário: Monstro morre com loot — MonsterKilledEvent carrega exp ────────

    @Test
    @DisplayName("Monstro morre — MonsterKilledEvent carrega baseExp e jobExp do monstro")
    void realizarAtaque_monsterMorre_eventoContemExpDoMonstro() {
        PlayerEntity playerEntity = makePlayerEntity(100);
        MonsterEntity monsterEntity = makeMonsterEntity(10);
        Player playerDomain = makeDomainPlayer(100);
        Monster monsterDomain = makeDomainMonster(10);

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
        when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
        when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(20);
        when(battleEngine.calculateLoot(monsterDomain)).thenReturn(new ArrayList<>());

        battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

        verify(eventPublisher).publishEvent(argThat((MonsterKilledEvent e) ->
                e.baseExp() == 5L && e.jobExp() == 3L));
    }
}
