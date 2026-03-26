package com.ragnarok.application.service;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.application.dto.SkillRowDTO;
import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SkillService skillService;

    @Autowired
    private SkillCombatService skillCombatService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSkillRepository playerSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillBuffEffectRepository skillBuffEffectRepository;

    @Autowired
    private MonsterRepository monsterRepository;

    @Autowired
    private CacheManager cacheManager;

    private static final Long PLAYER_ID = 1L;

    @BeforeEach
    void limparSkillsDoPlayer() {
        var cache = cacheManager.getCache("playerSkills");
        if (cache != null) cache.evict(PLAYER_ID);

        playerSkillRepository.findByPlayerId(PLAYER_ID)
                .forEach(s -> playerSkillRepository.delete(s));
    }

    @Test
    @DisplayName("listar deve retornar skills da classe do player")
    void listar_deveRetornarSkillsDaClasse() {
        List<SkillRowDTO> lista = skillService.listarSkillsDoPlayer(PLAYER_ID);

        assertFalse(lista.isEmpty(), "Lista de skills não deve estar vazia para Novice");

        SkillRowDTO qualquer = lista.get(0);
        assertNotNull(qualquer.aegisName(), "aegisName não pode ser null");
        assertNotNull(qualquer.name(), "name não pode ser null");
        assertTrue(qualquer.maxLevel() > 0, "maxLevel deve ser > 0");
        assertEquals(0, qualquer.currentLevel(), "currentLevel deve ser 0 antes de aprender");
    }

    @Test
    @DisplayName("listar deve marcar skill como disponivel quando sem prereqs")
    void listar_deveMarcarSkillSemPrereqComoDisponivel() {
        var p = playerRepository.findById(PLAYER_ID).orElseThrow();
        p.setSkillPoints(5);
        playerRepository.save(p);

        List<SkillRowDTO> lista = skillService.listarSkillsDoPlayer(PLAYER_ID);

        boolean temAlgumaDisponivel = lista.stream().anyMatch(SkillRowDTO::canLearn);
        assertTrue(temAlgumaDisponivel, "Deve haver ao menos uma skill disponivel com skillPoints > 0");
    }

    @Test
    @DisplayName("usarSkillEmCombate lança exceção quando skill não aprendida")
    void usarSkill_naoAprendida_lancaExcecao() {
        // player_skills está vazio (limpo no @BeforeEach) — NV_BASIC não foi aprendida
        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(PLAYER_ID, "NV_BASIC", null));
    }

    @Test
    @DisplayName("listarSkillsUsaveisForaDeCombate retorna lista não-nula")
    void listarSkillsUsaveis_semSkillsAprendidas_retornaListaVazia() {
        // player_skills vazio (limpo no @BeforeEach)
        var usaveis = skillService.listarSkillsUsaveisForaDeCombate(PLAYER_ID);
        assertNotNull(usaveis, "Deve retornar lista não-nula mesmo que vazia");
        assertTrue(usaveis.isEmpty(), "Sem skills aprendidas deve retornar lista vazia");
    }

    @Test
    @Transactional
    @DisplayName("usarSkillEmCombate HEAL com damageFormula restaura HP do player")
    void usarSkill_heal_restauraHP() {
        // Cria skill de cura com fórmula fixa
        SkillEntity skillHeal = new SkillEntity();
        skillHeal.setId(99901L);
        skillHeal.setAegisName("TEST_HEAL_99");
        skillHeal.setEffectType("HEAL");
        skillHeal.setSpCost(5);
        skillHeal.setDamageFormula("50");
        skillRepository.save(skillHeal);

        // Player aprende a skill
        PlayerSkillEntity ps = new PlayerSkillEntity();
        ps.setPlayerId(PLAYER_ID);
        ps.setSkillId("TEST_HEAL_99");
        ps.setCurrentLevel(1);
        playerSkillRepository.save(ps);

        // Player com HP parcial e SP suficiente
        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSpCurrent(50);
        player.setSpMax(100);
        player.setHpCurrent(30);
        player.setHpMax(100);
        playerRepository.save(player);

        String result = skillCombatService.usarSkillEmCombate(PLAYER_ID, "TEST_HEAL_99", null);

        assertTrue(result.contains("50"), "Deve reportar 50 de HP curado");
        var atualizado = playerRepository.findById(PLAYER_ID).orElseThrow();
        assertEquals(80, atualizado.getHpCurrent(), "HP deve ser 80 após cura de 50");
    }

    @Test
    @Transactional
    @DisplayName("usarSkillEmCombate BUFF adiciona buff ativo ao player")
    void usarSkill_buff_adicionaBuff() {
        // Cria skill de buff
        SkillEntity skillBuff = new SkillEntity();
        skillBuff.setId(99902L);
        skillBuff.setAegisName("TEST_BUFF_99");
        skillBuff.setEffectType("BUFF");
        skillBuff.setSpCost(5);
        skillBuff.setDurationTurns(5);
        skillRepository.save(skillBuff);

        // Efeito do buff: STR + skill_lv * 3
        SkillBuffEffectEntity buffEffect = new SkillBuffEffectEntity();
        buffEffect.setSkillId(99902L);
        buffEffect.setStatType("STR");
        buffEffect.setValueFormula("skill_lv * 3");
        skillBuffEffectRepository.save(buffEffect);

        // Player aprende a skill no nível 2
        PlayerSkillEntity ps = new PlayerSkillEntity();
        ps.setPlayerId(PLAYER_ID);
        ps.setSkillId("TEST_BUFF_99");
        ps.setCurrentLevel(2);
        playerSkillRepository.save(ps);

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSpCurrent(50);
        player.setSpMax(100);
        playerRepository.save(player);

        String result = skillCombatService.usarSkillEmCombate(PLAYER_ID, "TEST_BUFF_99", null);

        assertTrue(result.contains("5 turnos"), "Deve mencionar a duração de 5 turnos");
    }

    @Test
    @Transactional
    @DisplayName("usarSkillEmCombate lança exceção quando SP insuficiente")
    void usarSkill_spInsuficiente_lancaExcecao() {
        // Cria skill com custo de 30 SP
        SkillEntity skillHeal = new SkillEntity();
        skillHeal.setId(99903L);
        skillHeal.setAegisName("TEST_SPCHECK_99");
        skillHeal.setEffectType("HEAL");
        skillHeal.setSpCost(30);
        skillHeal.setDamageFormula("10");
        skillRepository.save(skillHeal);

        PlayerSkillEntity ps = new PlayerSkillEntity();
        ps.setPlayerId(PLAYER_ID);
        ps.setSkillId("TEST_SPCHECK_99");
        ps.setCurrentLevel(1);
        playerSkillRepository.save(ps);

        // Player com SP insuficiente
        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSpCurrent(5);
        playerRepository.save(player);

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(PLAYER_ID, "TEST_SPCHECK_99", null),
                "Deve lançar exceção por SP insuficiente");
    }

    @Test
    @Transactional
    @DisplayName("usarSkillEmCombate lança exceção quando skill é PASSIVE")
    void usarSkill_passive_lancaExcecao() {
        // Cria skill passiva
        SkillEntity skillPassive = new SkillEntity();
        skillPassive.setId(99904L);
        skillPassive.setAegisName("TEST_PASSIVE_99");
        skillPassive.setEffectType("PASSIVE");
        skillPassive.setSpCost(0);
        skillRepository.save(skillPassive);

        PlayerSkillEntity ps = new PlayerSkillEntity();
        ps.setPlayerId(PLAYER_ID);
        ps.setSkillId("TEST_PASSIVE_99");
        ps.setCurrentLevel(1);
        playerSkillRepository.save(ps);

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSpCurrent(50);
        playerRepository.save(player);

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(PLAYER_ID, "TEST_PASSIVE_99", null),
                "Deve lançar exceção pois skill passiva não pode ser usada manualmente");
    }

    @Test
    @Transactional
    @DisplayName("usarSkillEmCombate PHYSICAL_DAMAGE causa dano ao monstro")
    void usarSkill_physicalDamage_causaDanoAoMonstro() {
        // Cria monstro de teste
        MonsterEntity monster = new MonsterEntity();
        monster.setId(99991L);
        monster.setName("TestMob");
        monster.setSize("Medium");
        monster.setHp(100);
        monster.setDef(0);
        monsterRepository.save(monster);

        // Cria skill de dano físico com fórmula fixa
        SkillEntity skillPhys = new SkillEntity();
        skillPhys.setId(99905L);
        skillPhys.setAegisName("TEST_PHYS_99");
        skillPhys.setEffectType("PHYSICAL_DAMAGE");
        skillPhys.setSpCost(5);
        skillPhys.setDamageFormula("20");
        // sem elemento → neutro
        skillRepository.save(skillPhys);

        PlayerSkillEntity ps = new PlayerSkillEntity();
        ps.setPlayerId(PLAYER_ID);
        ps.setSkillId("TEST_PHYS_99");
        ps.setCurrentLevel(1);
        playerSkillRepository.save(ps);

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSpCurrent(50);
        player.setSpMax(100);
        playerRepository.save(player);

        String result = skillCombatService.usarSkillEmCombate(PLAYER_ID, "TEST_PHYS_99", 99991L);

        assertTrue(result.contains("causou"), "Deve reportar dano causado");
        assertTrue(result.contains("HP do monstro"), "Deve mencionar HP do monstro");
    }

    @Test
    @Transactional
    @DisplayName("usarSkillEmCombate BUFF sem efeitos configurados lança exceção")
    void usarSkill_buffSemEfeitos_lancaExcecao() {
        // Skill BUFF sem SkillBuffEffectEntity
        SkillEntity skillBuff = new SkillEntity();
        skillBuff.setId(99906L);
        skillBuff.setAegisName("TEST_BUFF_EMPTY_99");
        skillBuff.setEffectType("BUFF");
        skillBuff.setSpCost(5);
        skillBuff.setDurationTurns(5);
        skillRepository.save(skillBuff);
        // Não cria SkillBuffEffectEntity → buffEffects.isEmpty() == true

        PlayerSkillEntity ps = new PlayerSkillEntity();
        ps.setPlayerId(PLAYER_ID);
        ps.setSkillId("TEST_BUFF_EMPTY_99");
        ps.setCurrentLevel(1);
        playerSkillRepository.save(ps);

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSpCurrent(50);
        playerRepository.save(player);

        assertThrows(GameException.class,
                () -> skillCombatService.usarSkillEmCombate(PLAYER_ID, "TEST_BUFF_EMPTY_99", null),
                "Deve lançar exceção quando BUFF não tem efeitos configurados");
    }
}
