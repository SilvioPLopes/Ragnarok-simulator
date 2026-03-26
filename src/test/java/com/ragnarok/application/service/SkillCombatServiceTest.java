package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.domain.exception.InsufficientSpException;
import com.ragnarok.domain.model.Monster;
import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.BuffSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillCombatServiceTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private PlayerSkillRepository playerSkillRepository;
    @Mock private MonsterRepository monsterRepository;
    @Mock private ScriptInterpreter scriptInterpreter;
    @Mock private SkillBuffEffectRepository skillBuffEffectRepository;
    @Mock private BuffSerializer buffSerializer;
    @Mock private BattleEngine battleEngine;
    @Mock private MonsterMapper monsterMapper;
    @Mock private WeaponSizeService weaponSizeService;
    @Mock private PlayerItemRepository playerItemRepository;

    @InjectMocks
    private SkillCombatService skillCombatService;

    private PlayerEntity player;
    private SkillEntity skill;
    private PlayerSkillEntity playerSkill;

    @BeforeEach
    void setup() {
        player = new PlayerEntity();
        player.setId(1L);
        player.setSpCurrent(50);
        player.setSpMax(100);
        player.setHpCurrent(80);
        player.setHpMax(100);

        skill = new SkillEntity();
        skill.setId(10L);
        skill.setAegisName("TEST_SKILL");
        skill.setSpCost(10);

        playerSkill = new PlayerSkillEntity();
        playerSkill.setPlayerId(1L);
        playerSkill.setSkillId("TEST_SKILL");
        playerSkill.setCurrentLevel(1);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(skillRepository.findByAegisName("TEST_SKILL")).thenReturn(Optional.of(skill));
        when(playerSkillRepository.findByPlayerIdAndSkillId(1L, "TEST_SKILL"))
                .thenReturn(Optional.of(playerSkill));
    }

    // ── Skill não encontrada ──────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate: skill inexistente lança GameException")
    void skillNaoEncontrada_lancaExcecao() {
        when(skillRepository.findByAegisName("INEXISTENTE")).thenReturn(Optional.empty());

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(1L, "INEXISTENTE", null));
    }

    // ── Skill não aprendida ───────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate: skill não aprendida lança GameException")
    void skillNaoAprendida_lancaExcecao() {
        when(playerSkillRepository.findByPlayerIdAndSkillId(1L, "TEST_SKILL"))
                .thenReturn(Optional.empty());

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null));
    }

    // ── SP insuficiente ───────────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate: SP insuficiente lança InsufficientSpException")
    void spInsuficiente_lancaExcecao() {
        skill.setSpCost(100);
        player.setSpCurrent(5);

        assertThrows(InsufficientSpException.class,
                () -> skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null));
    }

    // ── Skill PASSIVE ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate: skill PASSIVE lança GameException")
    void skillPassiva_lancaExcecao() {
        skill.setEffectType("PASSIVE");

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null));
    }

    // ── HEAL ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate HEAL: restaura HP e desconta SP")
    void heal_restauraHP() {
        player.setHpCurrent(60); // 60 + 30 = 90 (abaixo do max 100)
        skill.setEffectType("HEAL");
        skill.setDamageFormula("30");
        when(scriptInterpreter.evaluateFormula(eq("30"), any())).thenReturn(30);

        String result = skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null);

        assertTrue(result.contains("30"), "Deve mencionar 30 de HP curado");
        verify(playerRepository).save(argThat(p -> p.getSpCurrent() == 40 && p.getHpCurrent() == 90));
    }

    // ── BUFF ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate BUFF: aplica buff e retorna mensagem com turnos")
    void buff_aplicaBuffComDuracao() {
        skill.setEffectType("BUFF");
        skill.setDurationTurns(5);

        SkillBuffEffectEntity effect = new SkillBuffEffectEntity();
        effect.setStatType("STR");
        effect.setValueFormula("skill_lv * 3");

        when(skillBuffEffectRepository.findBySkillId(10L)).thenReturn(List.of(effect));
        when(buffSerializer.fromJson(any())).thenReturn(new java.util.ArrayList<>());
        when(scriptInterpreter.evaluateFormula(eq("skill_lv * 3"), any())).thenReturn(3);
        when(buffSerializer.toJson(any())).thenReturn("[]");

        String result = skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null);

        assertTrue(result.contains("5 turnos"), "Deve mencionar duração de 5 turnos");
        assertTrue(result.contains("STR"), "Deve mencionar o stat bufado");
    }

    @Test
    @DisplayName("usarSkillEmCombate BUFF sem efeitos configurados: lança GameException")
    void buffSemEfeitos_lancaExcecao() {
        skill.setEffectType("BUFF");
        when(skillBuffEffectRepository.findBySkillId(10L)).thenReturn(List.of());

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null));
    }

    // ── PHYSICAL_DAMAGE ───────────────────────────────────────────────────────

    @Test
    @DisplayName("usarSkillEmCombate PHYSICAL_DAMAGE: causa dano ao monstro")
    void physicalDamage_causaDanoAoMonstro() {
        skill.setEffectType("PHYSICAL_DAMAGE");
        skill.setDamageFormula("50");

        MonsterEntity monsterEntity = new MonsterEntity();
        monsterEntity.setId(99L);
        monsterEntity.setHp(100);
        monsterEntity.setSize("Medium");

        Monster monster = new Monster();
        monster.setSize("Medium");

        when(monsterRepository.findById(99L)).thenReturn(Optional.of(monsterEntity));
        when(monsterMapper.toDomain(monsterEntity)).thenReturn(monster);
        when(scriptInterpreter.evaluateFormula(eq("50"), any())).thenReturn(50);
        when(battleEngine.applyElementModifier(eq(50), any(), eq(monster))).thenReturn(50);
        when(playerItemRepository.findByPlayerIdAndEquippedTrue(1L)).thenReturn(List.of());
        when(weaponSizeService.getModifier(WeaponType.NONE, "Medium")).thenReturn(100);
        when(battleEngine.applyWeaponSizeModifier(50, 100)).thenReturn(50);

        String result = skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", 99L);

        assertTrue(result.contains("causou"), "Deve mencionar dano causado");
        verify(monsterRepository).save(argThat(m -> m.getHp() == 50));
    }

    @Test
    @DisplayName("usarSkillEmCombate PHYSICAL_DAMAGE sem alvo: lança GameException")
    void physicalDamage_semAlvo_lancaExcecao() {
        skill.setEffectType("PHYSICAL_DAMAGE");
        skill.setDamageFormula("50");
        when(scriptInterpreter.evaluateFormula(eq("50"), any())).thenReturn(50);

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(1L, "TEST_SKILL", null));
    }
}
