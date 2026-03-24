package com.ragnarok.application.service;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassChangeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ClassChangeService classChangeService;

    @Autowired
    private PlayerRepository playerRepository;

    private static final Long PLAYER_ID = 1L;

    @BeforeEach
    void resetPlayer() {
        PlayerEntity p = playerRepository.findById(PLAYER_ID).orElseThrow();
        p.setJobClass("NOVICE");
        p.setJobLevel(9);
        p.setJobExp(0L);
        p.setSkillPoints(3);
        playerRepository.save(p);
    }

    @Test
    @DisplayName("Novice com jobLevel 9 troca para SWORDSMAN e reseta jobLevel/jobExp")
    void trocar_noviceParaSwordsman_persisteNoBanco() {
        classChangeService.trocarClasse(PLAYER_ID, JobClass.SWORDSMAN);

        PlayerEntity depois = playerRepository.findById(PLAYER_ID).orElseThrow();
        assertEquals("SWORDSMAN", depois.getJobClass());
        assertEquals(1, depois.getJobLevel());
        assertEquals(0L, depois.getJobExp());
        assertEquals(3, depois.getSkillPoints());
    }

    @Test
    @DisplayName("listarClassesDisponiveis retorna classes tier-1 do banco real para NOVICE")
    void listar_novice_retornaClassesDoBancoReal() {
        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(PLAYER_ID);

        assertFalse(disponiveis.isEmpty(), "Banco deve ter classes tier-1 na skill_tree");
        assertTrue(disponiveis.stream().allMatch(j -> j.tier == 1));
    }
}
