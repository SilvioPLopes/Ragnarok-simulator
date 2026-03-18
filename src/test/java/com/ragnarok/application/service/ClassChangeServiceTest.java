package com.ragnarok.application.service;

import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.SkillTreeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassChangeServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private SkillTreeRepository skillTreeRepository;

    @InjectMocks
    private ClassChangeService classChangeService;

    /**
     * Stub leniente: alguns testes (ex: tier>=2) lançam exceção antes de
     * chamar findDistinctJobClasses(). Com STRICT_STUBS (padrão do MockitoExtension),
     * stubs não consumidos causam UnnecessaryStubbingException. lenient() evita isso.
     */
    @BeforeEach
    void setupDbMock() {
        lenient().when(skillTreeRepository.findDistinctJobClasses())
                .thenReturn(List.of(
                        "SWORDSMAN", "MAGE", "ARCHER", "ACOLYTE", "THIEF", "MERCHANT",
                        "KNIGHT", "CRUSADER", "WIZARD", "SAGE", "HUNTER",
                        "PRIEST", "MONK", "ASSASSIN", "ROGUE", "BLACKSMITH", "ALCHEMIST"
                ));
    }

    private PlayerEntity makePlayer(String jobClass, int jobLevel, int skillPoints) {
        PlayerEntity p = new PlayerEntity();
        p.setId(1L);
        p.setJobClass(jobClass);
        p.setJobLevel(jobLevel);
        p.setJobExp(0L);
        p.setSkillPoints(skillPoints);
        return p;
    }

    // ── listarClassesDisponiveis ───────────────────────────────────────────────

    @Test
    @DisplayName("NOVICE retorna todas as tier-1 presentes no banco")
    void listar_novice_retornaTier1DoBanco() {
        PlayerEntity p = makePlayer("NOVICE", 1, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(1L);

        assertFalse(disponiveis.isEmpty());
        assertTrue(disponiveis.stream().allMatch(j -> j.tier == 1));
        assertTrue(disponiveis.contains(JobClass.SWORDSMAN));
    }

    @Test
    @DisplayName("KNIGHT (tier 2) retorna lista vazia sem chamar o banco")
    void listar_tier2_retornaVazio() {
        PlayerEntity p = makePlayer("KNIGHT", 1, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(1L);

        assertTrue(disponiveis.isEmpty());
        verify(skillTreeRepository, never()).findDistinctJobClasses();
    }

    @Test
    @DisplayName("SUPER_NOVICE retorna lista vazia sem chamar o banco")
    void listar_superNovice_retornaVazio() {
        PlayerEntity p = makePlayer("SUPER_NOVICE", 1, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(1L);

        assertTrue(disponiveis.isEmpty());
        verify(skillTreeRepository, never()).findDistinctJobClasses();
    }

    @Test
    @DisplayName("SWORDSMAN retorna KNIGHT e CRUSADER (suas nextClasses no banco)")
    void listar_swordsman_retornaNextClasses() {
        PlayerEntity p = makePlayer("SWORDSMAN", 40, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(1L);

        assertTrue(disponiveis.contains(JobClass.KNIGHT));
        assertTrue(disponiveis.contains(JobClass.CRUSADER));
        assertEquals(2, disponiveis.size());
    }

    // ── trocarClasse — casos válidos ──────────────────────────────────────────

    @Test
    @DisplayName("Novice com jobLevel 9 troca para SWORDSMAN com sucesso")
    void trocar_novice_jobLevel9_paraSwordsman() {
        PlayerEntity p = makePlayer("NOVICE", 9, 3);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        classChangeService.trocarClasse(1L, JobClass.SWORDSMAN);

        assertEquals("SWORDSMAN", p.getJobClass());
        assertEquals(1, p.getJobLevel());
        assertEquals(0L, p.getJobExp());
        assertEquals(3, p.getSkillPoints());
        verify(playerRepository).save(p);
    }

    @Test
    @DisplayName("Swordsman com jobLevel 40 troca para KNIGHT")
    void trocar_swordsman_jobLevel40_paraKnight() {
        PlayerEntity p = makePlayer("SWORDSMAN", 40, 5);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        classChangeService.trocarClasse(1L, JobClass.KNIGHT);

        assertEquals("KNIGHT", p.getJobClass());
        assertEquals(1, p.getJobLevel());
        assertEquals(0L, p.getJobExp());
        assertEquals(5, p.getSkillPoints());
        verify(playerRepository).save(p);
    }

    // ── trocarClasse — validações ─────────────────────────────────────────────

    @Test
    @DisplayName("Novice com jobLevel 8 lança exceção (insuficiente)")
    void trocar_novice_jobLevel8_lancaExcecao() {
        PlayerEntity p = makePlayer("NOVICE", 8, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.SWORDSMAN));
        assertTrue(ex.getMessage().contains("9"));
    }

    @Test
    @DisplayName("Swordsman com jobLevel 39 lança exceção (insuficiente)")
    void trocar_swordsman_jobLevel39_lancaExcecao() {
        PlayerEntity p = makePlayer("SWORDSMAN", 39, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.KNIGHT));
        assertTrue(ex.getMessage().contains("40"));
    }

    @Test
    @DisplayName("KNIGHT (tier 2) lança exceção de troca não disponível")
    void trocar_knight_lancaExcecao() {
        PlayerEntity p = makePlayer("KNIGHT", 50, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.LORD_KNIGHT));
    }

    @Test
    @DisplayName("SUPER_NOVICE lança exceção de troca não disponível")
    void trocar_superNovice_lancaExcecao() {
        PlayerEntity p = makePlayer("SUPER_NOVICE", 9, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.SWORDSMAN));
    }

    @Test
    @DisplayName("SUMMONER lança exceção de troca não disponível")
    void trocar_summoner_lancaExcecao() {
        PlayerEntity p = makePlayer("SUMMONER", 9, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.SWORDSMAN));
    }

    @Test
    @DisplayName("Classe inválida para progressão lança exceção")
    void trocar_classeInvalidaParaProgressao_lancaExcecao() {
        PlayerEntity p = makePlayer("NOVICE", 9, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.KNIGHT));
        assertTrue(ex.getMessage().toLowerCase().contains("inválida") ||
                   ex.getMessage().toLowerCase().contains("invalida"));
    }

    @Test
    @DisplayName("jobClass null lança IllegalStateException")
    void trocar_jobClassNull_lancaExcecao() {
        PlayerEntity p = makePlayer(null, 9, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.SWORDSMAN));
    }

    @Test
    @DisplayName("trocarClasse lança exceção quando jobLevel insuficiente (NOVICE jobLevel 5)")
    void trocarClasse_jobLevelInsuficiente_lancaExcecao() {
        // Player NOVICE precisa jobLevel >= 9; jobLevel 5 é insuficiente
        PlayerEntity p = makePlayer("NOVICE", 5, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.SWORDSMAN));
    }
}
