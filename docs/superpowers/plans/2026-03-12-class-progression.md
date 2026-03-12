# Class Progression Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement job/base level caps in LevelingService and an NPC-driven class change flow in the terminal.

**Architecture:** Three layers are touched in dependency order: (1) domain `LevelingService` gets level cap guards, (2) new `ClassChangeService` in application layer with `SkillTreeRepository` query, (3) `RagnarokTerminalRunner` gains an NPC menu that delegates entirely to `ClassChangeService`.

**Tech Stack:** Java 17, Spring Boot 3.4.2, JUnit 5, Mockito (via spring-boot-starter-test), PostgreSQL, Maven (`./mvnw`).

**Spec:** `docs/superpowers/specs/2026-03-12-class-progression-design.md`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/test/java/com/ragnarok/domain/service/LevelingServiceTest.java` | Unit tests for level cap logic |
| Modify | `src/main/java/com/ragnarok/domain/service/LevelingService.java` | Add base/job level caps |
| Modify | `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java` | Add `findDistinctJobClasses()` |
| Create | `src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java` | Unit tests (mocked repos) |
| Create | `src/main/java/com/ragnarok/application/service/ClassChangeService.java` | New application service |
| Create | `src/test/java/com/ragnarok/application/service/ClassChangeIntegrationTest.java` | Integration test (real DB) |
| Modify | `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java` | Add NPC menu option |

---

## Chunk 1: LevelingService Level Caps

### Task 1: Write failing tests for LevelingService

**Files:**
- Create: `src/test/java/com/ragnarok/domain/service/LevelingServiceTest.java`

- [ ] **Step 1.1: Create the test file**

```java
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
```

- [ ] **Step 1.2: Run to confirm all tests fail**

```bash
./mvnw test -Dtest=LevelingServiceTest -pl . 2>&1 | tail -20
```

Expected: compilation errors (caps not implemented) or test failures.

- [ ] **Step 1.3: Commit test file**

```bash
git add src/test/java/com/ragnarok/domain/service/LevelingServiceTest.java
git commit -m "test: add LevelingServiceTest for base/job level caps"
```

---

### Task 2: Implement level cap logic in LevelingService

**Files:**
- Modify: `src/main/java/com/ragnarok/domain/service/LevelingService.java`

- [ ] **Step 2.1: Replace processarExperiencia with capped version**

Replace the entire `processarExperiencia` method with:

```java
public String processarExperiencia(Player player, long gainedBaseExp, long gainedJobExp) {
    StringBuilder log = new StringBuilder();

    // Resolve JobClass para obter os caps de nível
    com.ragnarok.domain.model.JobClass jobClass;
    try {
        jobClass = com.ragnarok.domain.model.JobClass.valueOf(player.getJobClass());
    } catch (IllegalArgumentException | NullPointerException e) {
        throw new IllegalStateException("JobClass inválida ou não definida: " + player.getJobClass());
    }
    int maxJobLevel = jobClass.maxJobLevel();

    // 1. Base EXP — não adiciona se já no cap 99
    if (player.getBaseLevel() >= 99) {
        log.append(" (base nível máximo atingido)");
    } else {
        player.setBaseExp(player.getBaseExp() + gainedBaseExp);
        log.append(String.format(" (+%d Base XP)", gainedBaseExp));

        while (player.getBaseLevel() < 99
                && player.getBaseExp() >= calculateRequiredBaseExp(player.getBaseLevel())) {
            long req = calculateRequiredBaseExp(player.getBaseLevel());
            player.setBaseExp(player.getBaseExp() - req);
            player.setBaseLevel(player.getBaseLevel() + 1);

            int currentPoints = player.getStatPoints() != null ? player.getStatPoints() : 0;
            player.setStatPoints(currentPoints + 5);

            log.append("\n🎉 LEVEL UP! Nível Base ").append(player.getBaseLevel()).append(" alcançado!");
            log.append(" (+5 Pontos de Status)");

            player.setHpCurrent(player.getStats().getMaxHp());
            player.setSpCurrent(player.getStats().getMaxSp());
        }
    }

    // 2. Job EXP — não adiciona se já no cap da classe
    if (player.getJobLevel() >= maxJobLevel) {
        log.append(" (job nível máximo atingido)");
    } else {
        player.setJobExp(player.getJobExp() + gainedJobExp);
        log.append(String.format(" (+%d Job XP)", gainedJobExp));

        while (player.getJobLevel() < maxJobLevel
                && player.getJobExp() >= calculateRequiredJobExp(player.getJobLevel())) {
            long req = calculateRequiredJobExp(player.getJobLevel());
            player.setJobExp(player.getJobExp() - req);
            player.setJobLevel(player.getJobLevel() + 1);

            int currentSkillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
            player.setSkillPoints(currentSkillPoints + 1);

            log.append("\n🌟 JOB UP! Nível de Classe ").append(player.getJobLevel()).append(" alcançado!");
            log.append(" (+1 Ponto de Habilidade)");
        }
    }

    return log.toString();
}
```

- [ ] **Step 2.2: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=LevelingServiceTest -pl . 2>&1 | tail -20
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 2.3: Commit implementation**

```bash
git add src/main/java/com/ragnarok/domain/service/LevelingService.java
git commit -m "feat: add base/job level caps to LevelingService"
```

---

## Chunk 2: ClassChangeService

### Task 3: Add findDistinctJobClasses() to SkillTreeRepository

**Files:**
- Modify: `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java`

- [ ] **Step 3.1: Add the new query method**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SkillTreeRepository extends JpaRepository<SkillTreeEntity, Integer> {
    List<SkillTreeEntity> findByJobClassIgnoreCase(String jobClass);
    List<SkillTreeEntity> findByJobClassIgnoreCaseAndSkillId(String jobClass, String skillId);

    @Query("SELECT DISTINCT UPPER(s.jobClass) FROM SkillTreeEntity s")
    List<String> findDistinctJobClasses();
}
```

- [ ] **Step 3.2: Compile to verify no errors**

```bash
./mvnw compile -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3.3: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java
git commit -m "feat: add findDistinctJobClasses() to SkillTreeRepository"
```

---

### Task 4: Write failing unit tests for ClassChangeService

**Files:**
- Create: `src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java`

- [ ] **Step 4.1: Create test file**

```java
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
    @DisplayName("KNIGHT (tier 2) retorna lista vazia")
    void listar_tier2_retornaVazio() {
        PlayerEntity p = makePlayer("KNIGHT", 1, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(1L);

        assertTrue(disponiveis.isEmpty());
    }

    @Test
    @DisplayName("SUPER_NOVICE retorna lista vazia")
    void listar_superNovice_retornaVazio() {
        PlayerEntity p = makePlayer("SUPER_NOVICE", 1, 0);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(1L);

        assertTrue(disponiveis.isEmpty());
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
    void trocar_novice_jobLevel9_paraSwrodsman() {
        PlayerEntity p = makePlayer("NOVICE", 9, 3);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p));

        classChangeService.trocarClasse(1L, JobClass.SWORDSMAN);

        assertEquals("SWORDSMAN", p.getJobClass());
        assertEquals(1, p.getJobLevel());
        assertEquals(0L, p.getJobExp());
        assertEquals(3, p.getSkillPoints()); // skill points preservados
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
        assertEquals(5, p.getSkillPoints()); // preservados
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
        // NOVICE não pode ir para KNIGHT diretamente
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
}
```

- [ ] **Step 4.2: Run to confirm compilation fails (class doesn't exist yet)**

```bash
./mvnw test -Dtest=ClassChangeServiceTest -pl . 2>&1 | tail -10
```

Expected: compilation error `ClassChangeService not found`

- [ ] **Step 4.3: Commit test file**

```bash
git add src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java
git commit -m "test: add ClassChangeServiceTest (unit, mocked repos)"
```

---

### Task 5: Implement ClassChangeService

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/ClassChangeService.java`

- [ ] **Step 5.1: Create the service class**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.SkillTreeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassChangeService {

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;

    public ClassChangeService(PlayerRepository playerRepository,
                              SkillTreeRepository skillTreeRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
    }

    /**
     * Retorna as classes disponíveis para o player progredir.
     * NÃO verifica job level — isso é responsabilidade de trocarClasse().
     * Retorna lista vazia se a classe do player não tem caminho de progressão.
     */
    public List<JobClass> listarClassesDisponiveis(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Jogador não encontrado."));

        JobClass current = resolveJobClass(player.getJobClass());

        // Guard antes da chamada ao banco: evita query desnecessária para classes sem progressão
        if (current.tier >= 2 || (current.tier == 0 && current != JobClass.NOVICE)) {
            return Collections.emptyList();
        }

        Set<String> dbClasses = buildDbClassSet();

        if (current == JobClass.NOVICE) {
            return Arrays.stream(JobClass.values())
                    .filter(j -> j.tier == 1 && dbClasses.contains(j.name()))
                    .collect(Collectors.toList());
        }

        // tier == 1: retorna nextClasses() filtradas pelo banco
        return Arrays.stream(current.nextClasses())
                .filter(j -> dbClasses.contains(j.name()))
                .collect(Collectors.toList());
    }

    /**
     * Troca a classe do player após validar todas as regras.
     * Lança IllegalStateException com mensagem PT-BR em caso de violação.
     */
    @Transactional
    public void trocarClasse(Long playerId, JobClass novaClasse) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Jogador não encontrado."));

        JobClass current = resolveJobClass(player.getJobClass());

        // Guard: apenas NOVICE e tier-1 podem trocar de classe
        if (current.tier >= 2 || (current.tier == 0 && current != JobClass.NOVICE)) {
            throw new IllegalStateException("Troca de classe não disponível para esta classe.");
        }

        // Valida que a nova classe está disponível no banco
        Set<String> dbClasses = buildDbClassSet();
        List<JobClass> validTargets;

        if (current == JobClass.NOVICE) {
            validTargets = Arrays.stream(JobClass.values())
                    .filter(j -> j.tier == 1 && dbClasses.contains(j.name()))
                    .collect(Collectors.toList());
        } else {
            validTargets = Arrays.stream(current.nextClasses())
                    .filter(j -> dbClasses.contains(j.name()))
                    .collect(Collectors.toList());
        }

        if (!validTargets.contains(novaClasse)) {
            throw new IllegalStateException("Classe inválida para progressão.");
        }

        // Verifica job level
        int jobLevel = player.getJobLevel() != null ? player.getJobLevel() : 0;
        if (current == JobClass.NOVICE) {
            if (jobLevel < 9) {
                throw new IllegalStateException("Job level insuficiente. Necessário: 9");
            }
        } else if (current.tier == 1) {
            if (jobLevel < 40) {
                throw new IllegalStateException("Job level insuficiente. Necessário: 40");
            }
        } else {
            throw new IllegalStateException("Progressão de classe não suportada para este tier.");
        }

        // Aplica transição
        player.setJobClass(novaClasse.name());
        player.setJobLevel(1);
        player.setJobExp(0L);
        // skillPoints são mantidos intencionalmente
        playerRepository.save(player);
    }

    private JobClass resolveJobClass(String jobClassStr) {
        try {
            return JobClass.valueOf(jobClassStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("JobClass inválida ou não definida: " + jobClassStr);
        }
    }

    private Set<String> buildDbClassSet() {
        return new HashSet<>(skillTreeRepository.findDistinctJobClasses());
    }
}
```

- [ ] **Step 5.2: Run unit tests to confirm they pass**

```bash
./mvnw test -Dtest=ClassChangeServiceTest -pl . 2>&1 | tail -20
```

Expected: `Tests run: 12, Failures: 0, Errors: 0`

- [ ] **Step 5.3: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/ClassChangeService.java
git commit -m "feat: implement ClassChangeService with listarClassesDisponiveis and trocarClasse"
```

---

### Task 6: Integration test for ClassChangeService

**Files:**
- Create: `src/test/java/com/ragnarok/application/service/ClassChangeIntegrationTest.java`

**Pre-condition:** Requires `skill_tree` table to have at least one row with `job_class = 'swordsman'` (case-insensitive). The real DB seeded via `Migrate.py` satisfies this.

- [ ] **Step 6.1: Create integration test**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre"
})
class ClassChangeIntegrationTest {

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
        assertEquals(3, depois.getSkillPoints()); // skill points preservados
    }

    @Test
    @DisplayName("listarClassesDisponiveis retorna classes tier-1 do banco para NOVICE")
    void listar_novice_retornaClassesDoBancoReal() {
        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(PLAYER_ID);

        assertFalse(disponiveis.isEmpty(), "Banco deve ter classes tier-1 na skill_tree");
        assertTrue(disponiveis.stream().allMatch(j -> j.tier == 1));
    }
}
```

- [ ] **Step 6.2: Run integration test (requires DB)**

```bash
./mvnw test -Dtest=ClassChangeIntegrationTest -pl . 2>&1 | tail -20
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6.3: Commit**

```bash
git add src/test/java/com/ragnarok/application/service/ClassChangeIntegrationTest.java
git commit -m "test: add ClassChangeIntegrationTest with real DB"
```

---

## Chunk 3: Terminal NPC Menu

### Task 7: Add NPC option to RagnarokTerminalRunner

**Files:**
- Modify: `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java`

- [ ] **Step 7.1: Add imports and inject ClassChangeService into the runner**

Add these imports at the top of the file (with the existing imports):

```java
import com.ragnarok.application.service.ClassChangeService;
import com.ragnarok.domain.model.JobClass;
```

Add the field:

```java
private final ClassChangeService classChangeService;
```

Update constructor signature to:

```java
public RagnarokTerminalRunner(BattleService bs, PlayerService ps, ItemService is,
                              PlayerRepository pr, PlayerItemRepository pir,
                              MapMonsterRepository mmr, MonsterRepository mr,
                              PlayerMapper pm, MapPortalRepository portalRepo,
                              SkillService skillService,
                              ClassChangeService classChangeService) {
    this.battleService       = bs;
    this.playerService       = ps;
    this.itemService         = is;
    this.playerRepo          = pr;
    this.playerItemRepo      = pir;
    this.mapMonsterRepo      = mmr;
    this.monsterRepo         = mr;
    this.playerMapper        = pm;
    this.portalRepo          = portalRepo;
    this.skillService        = skillService;
    this.classChangeService  = classChangeService;
}
```

- [ ] **Step 7.2: Modify renderExplorationMenu() to show option 6 in Prontera**

Replace the `renderExplorationMenu` method with:

```java
private void renderExplorationMenu() {
    PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
    String mapaAtual = p.getMapName() != null ? p.getMapName() : "prontera";
    boolean emProntera = "prontera".equals(mapaAtual);

    System.out.println("\n[MAPA: " + mapaAtual + "] (HP: " + p.getHpCurrent() + "/" + p.getHpMax() + ")");
    System.out.println("1. Cacas monstros");
    System.out.println("2. Portais");
    System.out.println("3. Inventario");
    System.out.println("4. Ver Status");
    System.out.println("5. Sair");
    if (emProntera) {
        System.out.println("6. Falar com NPC");
    }
    System.out.print("> ");

    String input = scanner.nextLine();
    if ("1".equals(input))      caminhar(mapaAtual);
    else if ("2".equals(input)) renderPortaisMenu(mapaAtual);
    else if ("3".equals(input)) renderInventoryMenu();
    else if ("4".equals(input)) renderStatusMenu();
    else if ("5".equals(input)) System.exit(0);
    else if ("6".equals(input) && emProntera) renderNpcMenu();
}
```

- [ ] **Step 7.3: Add renderNpcMenu() method**

Add this method before `renderBattleMenu()`:

```java
private void renderNpcMenu() {
    // Delega ao service para obter classes disponíveis (não verifica job level aqui)
    List<JobClass> disponiveis =
            classChangeService.listarClassesDisponiveis(currentPlayer.getId());

    if (disponiveis.isEmpty()) {
        System.out.println("\nO NPC diz: Troca de classe nao disponivel para sua classe atual.");
        System.out.println("(Pressione ENTER para voltar)");
        scanner.nextLine();
        return;
    }

    PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
    JobClass currentClass;
    try {
        currentClass = JobClass.valueOf(p.getJobClass());
    } catch (Exception e) {
        System.out.println("Erro ao identificar classe do jogador.");
        return;
    }

    int required = (currentClass == JobClass.NOVICE) ? 9 : 40;

    System.out.println("\n=== NPC DE TROCA DE CLASSE ===");
    System.out.printf("Job Level atual: %d | Necessario: %d%n",
            p.getJobLevel() != null ? p.getJobLevel() : 0, required);
    System.out.println("Classes disponíveis:");

    for (int i = 0; i < disponiveis.size(); i++) {
        JobClass jc = disponiveis.get(i);
        System.out.printf("%d. %-20s — %s%n", (i + 1), jc.name(), jc.descricao);
    }
    System.out.println("0. Cancelar");
    System.out.print("> ");

    int escolha;
    try {
        escolha = Integer.parseInt(scanner.nextLine());
    } catch (NumberFormatException e) {
        System.out.println("Digite apenas numeros.");
        System.out.println("(Pressione ENTER para voltar)");
        scanner.nextLine();
        return;
    }

    if (escolha == 0) return;
    if (escolha < 1 || escolha > disponiveis.size()) {
        System.out.println("Opcao invalida.");
        System.out.println("(Pressione ENTER para voltar)");
        scanner.nextLine();
        return;
    }

    JobClass novaClasse = disponiveis.get(escolha - 1);

    try {
        classChangeService.trocarClasse(currentPlayer.getId(), novaClasse);
        System.out.println(">>> Voce se tornou um(a) " + novaClasse.descricao + "!");
        System.out.println("(Pressione ENTER para continuar)");
        scanner.nextLine();
    } catch (IllegalStateException e) {
        System.out.println(">>> " + e.getMessage());
        System.out.println("(Pressione ENTER para voltar)");
        scanner.nextLine();
    }
}
```

- [ ] **Step 7.4: Compile to verify no errors**

```bash
./mvnw compile -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7.5: Run full test suite to verify no regressions**

```bash
./mvnw test -pl . 2>&1 | tail -20
```

Expected: All tests pass (no new failures).

- [ ] **Step 7.6: Commit**

```bash
git add src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java
git commit -m "feat: add NPC class change menu to RagnarokTerminalRunner (Prontera only)"
```

---

## Final Verification

- [ ] **Run all tests one final time**

```bash
./mvnw test -pl . 2>&1 | tail -30
```

Expected: All tests pass including `LevelingServiceTest`, `ClassChangeServiceTest`, `ClassChangeIntegrationTest`.

- [ ] **Manual smoke test**

```bash
./mvnw spring-boot:run
```

1. Player starts at Prontera — verify option `6. Falar com NPC` appears.
2. Travel to another map — verify option 6 does NOT appear.
3. Return to Prontera — verify option 6 reappears.
4. Talk to NPC with NOVICE at job level < 9 — verify error message.
5. Set job level to 9 in DB, talk to NPC — verify class list appears and class change completes.
6. Kill monsters — verify job XP stops accumulating at `maxJobLevel()`.
