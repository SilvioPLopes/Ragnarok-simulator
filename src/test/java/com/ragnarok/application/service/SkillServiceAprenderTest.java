package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.PlayerSkillRepository;
import com.ragnarok.runner.RagnarokTerminalRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class SkillServiceAprenderTest {

    @MockBean
    @SuppressWarnings("unused")
    private RagnarokTerminalRunner ragnarokTerminalRunner;

    @Autowired
    private SkillService skillService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSkillRepository playerSkillRepository;

    private static final Long PLAYER_ID = 1L;

    @BeforeEach
    void setup() {
        // Limpa skills do player e garante que tem skill points
        playerSkillRepository.findByPlayerId(PLAYER_ID)
                .forEach(playerSkillRepository::delete);

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSkillPoints(5);
        playerRepository.save(player);
    }

    @Test
    @DisplayName("aprenderSkill deve aumentar currentLevel da skill")
    void aprenderSkill_deveIncrementarNivel() {
        // Pega uma skill disponível (sem prereq)
        var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
        var skillDisponivel = lista.stream()
                .filter(SkillRowDTO::canLearn)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhuma skill disponivel para aprender"));

        String aegisName = skillDisponivel.aegisName();
        skillService.aprenderSkill(PLAYER_ID, aegisName);

        // Verifica que o nivel subiu
        var depois = skillService.listarSkillsDoPlayer(PLAYER_ID);
        var atualizada = depois.stream()
                .filter(s -> s.aegisName().equals(aegisName))
                .findFirst()
                .orElseThrow();

        assertEquals(1, atualizada.currentLevel(), "currentLevel deve ser 1 após aprender");
    }

    @Test
    @DisplayName("aprenderSkill deve descontar 1 skill point do player")
    void aprenderSkill_deveDescontarSkillPoint() {
        var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
        var skillDisponivel = lista.stream()
                .filter(SkillRowDTO::canLearn)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhuma skill disponivel"));

        skillService.aprenderSkill(PLAYER_ID, skillDisponivel.aegisName());

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        assertEquals(4, player.getSkillPoints(), "Deve restar 4 skill points após aprender 1 skill");
    }

    @Test
    @DisplayName("aprenderSkill deve falhar se player não tem skill points")
    void aprenderSkill_deveFalharSemSkillPoints() {
        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSkillPoints(0);
        playerRepository.save(player);

        var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
        var qualquerSkill = lista.get(0);

        assertThrows(IllegalStateException.class,
                () -> skillService.aprenderSkill(PLAYER_ID, qualquerSkill.aegisName()),
                "Deve lançar exceção quando não há skill points");
    }

    @Test
    @DisplayName("aprenderSkill deve falhar se skill já está no nível máximo")
    void aprenderSkill_deveFalharSeNivelMaximo() {
        // Aprende a skill até o nível máximo
        var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
        var skill = lista.stream()
                .filter(SkillRowDTO::canLearn)
                .findFirst()
                .orElseThrow();

        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSkillPoints(99);
        playerRepository.save(player);

        // Aprende até maxLevel
        for (int i = 0; i < skill.maxLevel(); i++) {
            skillService.aprenderSkill(PLAYER_ID, skill.aegisName());
        }

        // Tenta aprender além do máximo
        assertThrows(IllegalStateException.class,
                () -> skillService.aprenderSkill(PLAYER_ID, skill.aegisName()),
                "Deve lançar exceção quando skill está no nível máximo");
    }

    @Test
    @DisplayName("aprenderSkill lança IllegalStateException quando sem Skill Points")
    void aprenderSkill_semSkillPoints_lancaExcecao() {
        var player = playerRepository.findById(PLAYER_ID).orElseThrow();
        player.setSkillPoints(0);
        playerRepository.save(player);

        // Pega qualquer skill da árvore — não importa qual, sem SP sempre lança
        var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
        if (lista.isEmpty()) return; // sem dados de skill_tree, teste não se aplica

        String qualquerSkill = lista.get(0).aegisName();
        assertThrows(IllegalStateException.class,
                () -> skillService.aprenderSkill(PLAYER_ID, qualquerSkill));
    }

    @Test
    @DisplayName("aprenderSkill lança IllegalStateException para skill inexistente na classe")
    void aprenderSkill_skillInexistente_lancaExcecao() {
        // BOWLING_BASH é skill de Knight — não existe na árvore do Novice (player ID=1)
        assertThrows(IllegalStateException.class,
                () -> skillService.aprenderSkill(PLAYER_ID, "BOWLING_BASH"));
    }

    @Test
    @DisplayName("listarSkillsDoPlayer retorna lista vazia quando player sem jobClass")
    void listarSkillsDoPlayer_jobClassNula_retornaListaVazia() {
        var tempPlayer = new com.ragnarok.infrastructure.persistence.PlayerEntity();
        tempPlayer.setName("SemClasse");
        tempPlayer.setJobClass(null);
        tempPlayer = playerRepository.save(tempPlayer);
        Long tempId = tempPlayer.getId();

        try {
            var lista = skillService.listarSkillsDoPlayer(tempId);
            assertTrue(lista.isEmpty(), "Player sem jobClass deve retornar lista vazia");
        } finally {
            playerRepository.deleteById(tempId);
        }
    }
}
