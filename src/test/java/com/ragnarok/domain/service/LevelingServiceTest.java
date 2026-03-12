package com.ragnarok.domain.service;

import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.model.PlayerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelingServiceTest {

    private LevelingService levelingService;

    @BeforeEach
    void setUp() {
        levelingService = new LevelingService();
    }

    /** Helper: constrói um Player mínimo para os testes */
    private Player makePlayer(String jobClass, int baseLevel, long baseExp, int jobLevel, long jobExp) {
        Player p = new Player();
        p.setJobClass(jobClass);
        p.setBaseLevel(baseLevel);
        p.setBaseExp(baseExp);
        p.setJobLevel(jobLevel);
        p.setJobExp(jobExp);
        p.setStatPoints(0);
        p.setSkillPoints(0);
        // Stats necessários para player.getStats().getMaxHp() no level-up
        p.setStats(new PlayerStats(1, 1, 1, 1, 1, 1, 100, 40));
        p.setHpCurrent(100);
        p.setSpCurrent(40);
        return p;
    }

    // ── Base level cap ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("baseExp NÃO é incrementado quando baseLevel == 99")
    void baseExp_naoIncrementaQuandoNivel99() {
        Player p = makePlayer("NOVICE", 99, 0L, 1, 0L);
        levelingService.processarExperiencia(p, 500, 0);
        assertEquals(99, p.getBaseLevel());
        assertEquals(0L, p.getBaseExp());
    }

    @Test
    @DisplayName("baseLevel para em 99 mesmo com enorme quantidade de exp")
    void baseLevel_naoUltrapassaCap99ComOverflow() {
        Player p = makePlayer("NOVICE", 97, 0L, 1, 0L);
        levelingService.processarExperiencia(p, 100_000, 0);
        assertEquals(99, p.getBaseLevel());
    }

    @Test
    @DisplayName("jogador sobe normalmente até 99 e para")
    void baseLevel_subeNormalmenteAte99() {
        // Level 98, precisa de 98*100 = 9800 exp para level 99
        Player p = makePlayer("SWORDSMAN", 98, 0L, 1, 0L);
        levelingService.processarExperiencia(p, 9800, 0);
        assertEquals(99, p.getBaseLevel());
        assertEquals(0L, p.getBaseExp()); // sem sobra
    }

    // ── Job level cap ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("jobExp NÃO é incrementado quando jobLevel == maxJobLevel (NOVICE=9)")
    void jobExp_naoIncrementaQuandoNoMaxJobLevel_novice() {
        Player p = makePlayer("NOVICE", 1, 0L, 9, 0L);
        levelingService.processarExperiencia(p, 0, 500);
        assertEquals(9, p.getJobLevel());
        assertEquals(0L, p.getJobExp());
    }

    @Test
    @DisplayName("jobLevel para em 50 com overflow de exp (SWORDSMAN)")
    void jobLevel_naoUltrapassaMaxJobLevelComOverflow() {
        Player p = makePlayer("SWORDSMAN", 1, 0L, 49, 0L);
        levelingService.processarExperiencia(p, 0, 100_000);
        assertEquals(50, p.getJobLevel());
    }

    // ── Defensive guard ────────────────────────────────────────────────────────

    @Test
    @DisplayName("jobClass null deve lançar IllegalStateException")
    void jobClassNull_lancaIllegalStateException() {
        Player p = makePlayer(null, 1, 0L, 1, 0L);
        assertThrows(IllegalStateException.class,
                () -> levelingService.processarExperiencia(p, 100, 100));
    }

    @Test
    @DisplayName("jobClass inválida deve lançar IllegalStateException")
    void jobClassInvalida_lancaIllegalStateException() {
        Player p = makePlayer("CLASSE_INEXISTENTE", 1, 0L, 1, 0L);
        assertThrows(IllegalStateException.class,
                () -> levelingService.processarExperiencia(p, 100, 100));
    }
}
