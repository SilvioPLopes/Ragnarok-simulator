package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.PlayerSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre"
})
class SkillServiceIntegrationTest {

    @Autowired
    private SkillService skillService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSkillRepository playerSkillRepository;

    private static final Long PLAYER_ID = 1L;

    @BeforeEach
    void limparSkillsDoPlayer() {
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
}
