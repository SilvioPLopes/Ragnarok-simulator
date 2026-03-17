# Skills System Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the skills system — data layer, application service, and terminal menu — so the player can view and learn skills using skillPoints.

**Architecture:** Three JPA entities map existing DB tables (`skills`, `skill_tree`) and a new table (`player_skills`). A `SkillService` in the application layer handles all business logic (listing, prerequisite validation, learning). `RagnarokTerminalRunner` gets a new `renderSkillsMenu()` method and an `"S"` entry point from `renderStatusMenu()`.

**Tech Stack:** Java 17, Spring Boot 3.4.2, Spring Data JPA, PostgreSQL, Lombok, JUnit 5 (`@SpringBootTest` integration tests hitting real DB)

---

## File Map

**New files:**
| File | Responsibility |
|---|---|
| `src/main/java/com/ragnarok/infrastructure/persistence/SkillEntity.java` | Maps `skills` table |
| `src/main/java/com/ragnarok/infrastructure/persistence/SkillRepository.java` | `findByAegisName()` |
| `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeEntity.java` | Maps `skill_tree` table (read-only, multiple rows per skill) |
| `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java` | `findByJobClassIgnoreCase()` |
| `src/main/java/com/ragnarok/infrastructure/persistence/PlayerSkillEntity.java` | Maps `player_skills` table (JPA creates it) |
| `src/main/java/com/ragnarok/infrastructure/persistence/PlayerSkillRepository.java` | `findByPlayerId()`, `findByPlayerIdAndSkillId()` |
| `src/main/java/com/ragnarok/application/service/SkillRowDTO.java` | Public record used by terminal to display one skill row |
| `src/main/java/com/ragnarok/application/service/SkillService.java` | `listarSkillsDoPlayer()`, `aprenderSkill()` |
| `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java` | Integration tests for SkillService |

**Modified files:**
| File | What changes |
|---|---|
| `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java` | Add `SkillService` dependency, `"S"` branch in `renderStatusMenu()`, new `renderSkillsMenu()` method |

---

## Chunk 1: Data Layer

### Task 1: SkillEntity + SkillRepository

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/SkillEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/SkillRepository.java`

- [ ] **Step 1.1: Create SkillEntity**

```java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skills")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillEntity {

    @Id
    private Long id;

    @Column(name = "aegis_name")
    private String aegisName;

    private String name;

    private String type;
}
```

- [ ] **Step 1.2: Create SkillRepository**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<SkillEntity, Long> {
    Optional<SkillEntity> findByAegisName(String aegisName);
}
```

- [ ] **Step 1.3: Run existing tests to make sure nothing broke**

```bash
./mvnw test -Dtest=RagnarokCoreApplicationTests
```

Expected: BUILD SUCCESS

- [ ] **Step 1.4: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/persistence/SkillEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/SkillRepository.java
git commit -m "feat: add SkillEntity and SkillRepository"
```

---

### Task 2: SkillTreeEntity + SkillTreeRepository

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java`

- [ ] **Step 2.1: Create SkillTreeEntity**

```java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skill_tree")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillTreeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "job_class")
    private String jobClass;

    @Column(name = "skill_id")
    private String skillId;

    @Column(name = "max_level")
    private Integer maxLevel;

    @Column(name = "prereq_skill")
    private String prereqSkill;

    @Column(name = "prereq_level")
    private Integer prereqLevel;
}
```

> Note: This entity is read-only — never call `save()` on it.

- [ ] **Step 2.2: Create SkillTreeRepository**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkillTreeRepository extends JpaRepository<SkillTreeEntity, Integer> {
    List<SkillTreeEntity> findByJobClassIgnoreCase(String jobClass);
}
```

- [ ] **Step 2.3: Run existing tests**

```bash
./mvnw test -Dtest=RagnarokCoreApplicationTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2.4: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/SkillTreeRepository.java
git commit -m "feat: add SkillTreeEntity and SkillTreeRepository"
```

---

### Task 3: PlayerSkillEntity + PlayerSkillRepository

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/PlayerSkillEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/PlayerSkillRepository.java`

- [ ] **Step 3.1: Create PlayerSkillEntity**

```java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "player_skills",
    uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "skill_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "skill_id", nullable = false)
    private String skillId;

    @Column(name = "current_level", nullable = false)
    private Integer currentLevel;
}
```

> JPA will auto-create `player_skills` via `ddl-auto=update` on next startup.

- [ ] **Step 3.2: Create PlayerSkillRepository**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerSkillRepository extends JpaRepository<PlayerSkillEntity, UUID> {
    List<PlayerSkillEntity> findByPlayerId(Long playerId);
    Optional<PlayerSkillEntity> findByPlayerIdAndSkillId(Long playerId, String skillId);
}
```

- [ ] **Step 3.3: Run existing tests**

```bash
./mvnw test -Dtest=RagnarokCoreApplicationTests
```

Expected: BUILD SUCCESS (table `player_skills` is created by JPA on startup)

- [ ] **Step 3.4: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/persistence/PlayerSkillEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/PlayerSkillRepository.java
git commit -m "feat: add PlayerSkillEntity and PlayerSkillRepository"
```

---

## Chunk 2: Application Service

### Task 4: SkillRowDTO

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/SkillRowDTO.java`

- [ ] **Step 4.1: Create SkillRowDTO**

```java
package com.ragnarok.application.service;

public record SkillRowDTO(
        String aegisName,
        String name,
        int maxLevel,
        int currentLevel,
        boolean canLearn,
        String blockedReason   // null when canLearn = true
) {}
```

- [ ] **Step 4.2: Compile check**

```bash
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4.3: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/SkillRowDTO.java
git commit -m "feat: add SkillRowDTO public record"
```

---

### Task 5: SkillService — listarSkillsDoPlayer

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/SkillService.java`
- Create: `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java`

- [ ] **Step 5.1: Write failing test for listarSkillsDoPlayer**

```java
package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.PlayerSkillRepository;
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

        // O player Hero é NOVICE — a tabela skill_tree tem skills para Novice
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
        // Garante que o player tem skillPoints
        var p = playerRepository.findById(PLAYER_ID).orElseThrow();
        p.setSkillPoints(5);
        playerRepository.save(p);

        List<SkillRowDTO> lista = skillService.listarSkillsDoPlayer(PLAYER_ID);

        // Pelo menos uma skill sem prereqs deve ser disponivel
        boolean temAlgumaDisponivel = lista.stream().anyMatch(SkillRowDTO::canLearn);
        assertTrue(temAlgumaDisponivel, "Deve haver ao menos uma skill disponivel com skillPoints > 0");
    }
}
```

- [ ] **Step 5.2: Run test to verify it fails (SkillService doesn't exist yet)**

```bash
./mvnw test -Dtest=SkillServiceIntegrationTest
```

Expected: COMPILE ERROR — `SkillService` not found

- [ ] **Step 5.3: Create SkillService with listarSkillsDoPlayer**

```java
package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;
    private final PlayerSkillRepository playerSkillRepository;

    public SkillService(PlayerRepository playerRepository,
                        SkillTreeRepository skillTreeRepository,
                        PlayerSkillRepository playerSkillRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
        this.playerSkillRepository = playerSkillRepository;
    }

    public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
        String jobClass = player.getJobClass();

        List<SkillTreeEntity> todasLinhas = skillTreeRepository.findByJobClassIgnoreCase(jobClass);
        if (todasLinhas.isEmpty()) return Collections.emptyList();

        // Carrega todas as skills do player de uma vez (evita N+1)
        Map<String, Integer> playerSkillLevels = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .collect(Collectors.toMap(PlayerSkillEntity::getSkillId, PlayerSkillEntity::getCurrentLevel));

        // Agrupa linhas por skillId (cada skill pode ter múltiplos prereqs)
        Map<String, List<SkillTreeEntity>> porSkill = todasLinhas.stream()
                .collect(Collectors.groupingBy(SkillTreeEntity::getSkillId));

        List<SkillRowDTO> resultado = new ArrayList<>();

        for (Map.Entry<String, List<SkillTreeEntity>> entry : porSkill.entrySet()) {
            String skillId = entry.getKey();
            List<SkillTreeEntity> linhas = entry.getValue();
            int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

            int currentLevel = playerSkillLevels.getOrDefault(skillId, 0);

            // Verifica todos os pré-requisitos (AND-logic, sem filtro de job_class)
            String blockedReason = null;

            if (currentLevel >= maxLevel) {
                blockedReason = "Nivel maximo atingido";
            } else {
                for (SkillTreeEntity linha : linhas) {
                    if (linha.getPrereqSkill() != null && !linha.getPrereqSkill().isBlank()) {
                        int prereqLevel = linha.getPrereqLevel() != null ? linha.getPrereqLevel() : 1;
                        int playerPrereqLevel = playerSkillLevels.getOrDefault(linha.getPrereqSkill(), 0);
                        if (playerPrereqLevel < prereqLevel) {
                            blockedReason = "Requer " + linha.getPrereqSkill() + " Lv" + prereqLevel;
                            break;
                        }
                    }
                }
                if (blockedReason == null && skillPoints <= 0) {
                    blockedReason = "Sem Skill Points";
                }
            }

            resultado.add(new SkillRowDTO(skillId, skillId, maxLevel, currentLevel,
                    blockedReason == null, blockedReason));
        }

        resultado.sort(Comparator.comparing(SkillRowDTO::aegisName));
        return resultado;
    }

    @Transactional
    public String aprenderSkill(Long playerId, String aegisName) {
        // Implementado na Task 6
        throw new UnsupportedOperationException("não implementado ainda");
    }
}
```

> Note: `name` field is set to `skillId` for now — Task 6 will join against `SkillEntity` to get the display name. This keeps Task 5 focused and passing.

- [ ] **Step 5.4: Run test to verify it passes**

```bash
./mvnw test -Dtest=SkillServiceIntegrationTest#listar_deveRetornarSkillsDaClasse+listar_deveMarcarSkillSemPrereqComoDisponivel
```

Expected: PASS

- [ ] **Step 5.5: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/SkillService.java \
        src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java
git commit -m "feat: implement SkillService.listarSkillsDoPlayer"
```

---

### Task 6: SkillService — aprenderSkill + display name from SkillEntity

**Files:**
- Modify: `src/main/java/com/ragnarok/application/service/SkillService.java`
- Modify: `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java`

- [ ] **Step 6.1: Write failing tests for aprenderSkill**

Add these tests to `SkillServiceIntegrationTest`:

```java
@Test
@DisplayName("aprender deve incrementar level e decrementar skillPoints")
void aprender_deveIncrementarLevelEDecrementarSkillPoints() {
    // Garante skillPoints disponíveis
    var p = playerRepository.findById(PLAYER_ID).orElseThrow();
    p.setSkillPoints(3);
    playerRepository.save(p);

    // Pega a primeira skill sem prereqs disponivel
    List<SkillRowDTO> lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
    SkillRowDTO skillDisponivel = lista.stream()
            .filter(SkillRowDTO::canLearn)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Nenhuma skill disponivel para testar"));

    String resultado = skillService.aprenderSkill(PLAYER_ID, skillDisponivel.aegisName());

    assertTrue(resultado.contains("Lv"), "Mensagem deve conter 'Lv': " + resultado);

    // Verifica level no banco
    var skill = playerSkillRepository
            .findByPlayerIdAndSkillId(PLAYER_ID, skillDisponivel.aegisName())
            .orElseThrow();
    assertEquals(1, skill.getCurrentLevel());

    // Verifica skillPoints decrementado
    var pAtualizado = playerRepository.findById(PLAYER_ID).orElseThrow();
    assertEquals(2, pAtualizado.getSkillPoints());
}

@Test
@DisplayName("aprender deve falhar se sem skillPoints")
void aprender_deveFalharSemSkillPoints() {
    // Encontra uma skill sem prereqs (disponivel com 1 ponto)
    var p = playerRepository.findById(PLAYER_ID).orElseThrow();
    p.setSkillPoints(1);
    playerRepository.save(p);

    String skillId = skillService.listarSkillsDoPlayer(PLAYER_ID).stream()
            .filter(SkillRowDTO::canLearn)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Nenhuma skill disponivel"))
            .aegisName();

    // Agora zera os pontos e tenta aprender
    p.setSkillPoints(0);
    playerRepository.save(p);

    String resultado = skillService.aprenderSkill(PLAYER_ID, skillId);

    assertTrue(resultado.toLowerCase().contains("skill point"),
            "Mensagem deve indicar falta de skill points: " + resultado);
}

@Test
@DisplayName("aprender skill duas vezes deve chegar a Lv 2")
void aprender_duasVezesDeveChegar_Lv2() {
    var p = playerRepository.findById(PLAYER_ID).orElseThrow();
    p.setSkillPoints(5);
    playerRepository.save(p);

    List<SkillRowDTO> lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
    String skillId = lista.stream()
            .filter(s -> s.canLearn() && s.maxLevel() >= 2)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Nenhuma skill com maxLevel >= 2 disponivel"))
            .aegisName();

    skillService.aprenderSkill(PLAYER_ID, skillId);
    skillService.aprenderSkill(PLAYER_ID, skillId);

    int level = playerSkillRepository
            .findByPlayerIdAndSkillId(PLAYER_ID, skillId)
            .map(PlayerSkillEntity::getCurrentLevel)
            .orElse(0);
    assertEquals(2, level);
}
```

- [ ] **Step 6.2: Run tests to verify they fail**

```bash
./mvnw test -Dtest=SkillServiceIntegrationTest#aprender_deveIncrementarLevelEDecrementarSkillPoints
```

Expected: FAIL — `UnsupportedOperationException`

- [ ] **Step 6.3: Implement aprenderSkill and fix name lookup in listarSkillsDoPlayer**

Replace the full `SkillService.java` with this:

```java
package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;
    private final PlayerSkillRepository playerSkillRepository;
    private final SkillRepository skillRepository;

    public SkillService(PlayerRepository playerRepository,
                        SkillTreeRepository skillTreeRepository,
                        PlayerSkillRepository playerSkillRepository,
                        SkillRepository skillRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
        this.playerSkillRepository = playerSkillRepository;
        this.skillRepository = skillRepository;
    }

    public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
        String jobClass = player.getJobClass();

        List<SkillTreeEntity> todasLinhas = skillTreeRepository.findByJobClassIgnoreCase(jobClass);
        if (todasLinhas.isEmpty()) return Collections.emptyList();

        // Carrega todas as skills do player de uma vez (evita N+1)
        Map<String, Integer> playerSkillLevels = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .collect(Collectors.toMap(PlayerSkillEntity::getSkillId, PlayerSkillEntity::getCurrentLevel));

        Map<String, List<SkillTreeEntity>> porSkill = todasLinhas.stream()
                .collect(Collectors.groupingBy(SkillTreeEntity::getSkillId));

        List<SkillRowDTO> resultado = new ArrayList<>();

        for (Map.Entry<String, List<SkillTreeEntity>> entry : porSkill.entrySet()) {
            String skillId = entry.getKey();
            List<SkillTreeEntity> linhas = entry.getValue();
            int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

            // Display name via SkillEntity (fallback to aegisName if not found)
            String displayName = skillRepository.findByAegisName(skillId)
                    .map(SkillEntity::getName)
                    .orElse(skillId);

            int currentLevel = playerSkillLevels.getOrDefault(skillId, 0);

            String blockedReason = null;

            if (currentLevel >= maxLevel) {
                blockedReason = "Nivel maximo atingido";
            } else {
                for (SkillTreeEntity linha : linhas) {
                    if (linha.getPrereqSkill() != null && !linha.getPrereqSkill().isBlank()) {
                        int prereqLevel = linha.getPrereqLevel() != null ? linha.getPrereqLevel() : 1;
                        int playerPrereqLevel = playerSkillLevels.getOrDefault(linha.getPrereqSkill(), 0);
                        if (playerPrereqLevel < prereqLevel) {
                            blockedReason = "Requer " + linha.getPrereqSkill() + " Lv" + prereqLevel;
                            break;
                        }
                    }
                }
                if (blockedReason == null && skillPoints <= 0) {
                    blockedReason = "Sem Skill Points";
                }
            }

            resultado.add(new SkillRowDTO(skillId, displayName, maxLevel, currentLevel,
                    blockedReason == null, blockedReason));
        }

        resultado.sort(Comparator.comparing(SkillRowDTO::aegisName));
        return resultado;
    }

    @Transactional
    public String aprenderSkill(Long playerId, String aegisName) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
        String jobClass = player.getJobClass();

        // 1. Skill existe para a classe do player
        List<SkillTreeEntity> linhas = skillTreeRepository.findByJobClassIgnoreCase(jobClass)
                .stream()
                .filter(l -> aegisName.equalsIgnoreCase(l.getSkillId()))
                .toList();

        if (linhas.isEmpty()) {
            return "Skill " + aegisName + " nao disponivel para sua classe.";
        }

        int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

        // 2. Todos os pré-requisitos satisfeitos
        for (SkillTreeEntity linha : linhas) {
            if (linha.getPrereqSkill() != null && !linha.getPrereqSkill().isBlank()) {
                int prereqLevel = linha.getPrereqLevel() != null ? linha.getPrereqLevel() : 1;
                int playerPrereqLevel = playerSkillRepository
                        .findByPlayerIdAndSkillId(playerId, linha.getPrereqSkill())
                        .map(PlayerSkillEntity::getCurrentLevel)
                        .orElse(0);
                if (playerPrereqLevel < prereqLevel) {
                    return "Requer " + linha.getPrereqSkill() + " Lv" + prereqLevel;
                }
            }
        }

        // 3. Nível máximo
        int currentLevel = playerSkillRepository
                .findByPlayerIdAndSkillId(playerId, aegisName)
                .map(PlayerSkillEntity::getCurrentLevel)
                .orElse(0);

        if (currentLevel >= maxLevel) {
            return "Skill " + aegisName + " ja esta no nivel maximo (" + maxLevel + ").";
        }

        // 4. Skill Points
        if (skillPoints <= 0) {
            return "Sem Skill Points disponíveis.";
        }

        // Upsert PlayerSkillEntity
        PlayerSkillEntity playerSkill = playerSkillRepository
                .findByPlayerIdAndSkillId(playerId, aegisName)
                .orElseGet(() -> {
                    PlayerSkillEntity novo = new PlayerSkillEntity();
                    novo.setPlayerId(playerId);
                    novo.setSkillId(aegisName);
                    novo.setCurrentLevel(0);
                    return novo;
                });

        int novoLevel = playerSkill.getCurrentLevel() + 1;
        playerSkill.setCurrentLevel(novoLevel);
        playerSkillRepository.save(playerSkill);

        player.setSkillPoints(skillPoints - 1);
        playerRepository.save(player);

        String displayName = skillRepository.findByAegisName(aegisName)
                .map(SkillEntity::getName)
                .orElse(aegisName);

        return ">>> [" + displayName + "] subiu para Lv " + novoLevel + "!";
    }
}
```

- [ ] **Step 6.4: Run all SkillService tests**

```bash
./mvnw test -Dtest=SkillServiceIntegrationTest
```

Expected: All tests PASS

- [ ] **Step 6.5: Run full test suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS

- [ ] **Step 6.6: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/SkillService.java \
        src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java
git commit -m "feat: implement SkillService.aprenderSkill with prereq validation"
```

---

## Chunk 3: Terminal UI

### Task 7: Skills menu in RagnarokTerminalRunner

**Files:**
- Modify: `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java`

- [ ] **Step 7.1: Add SkillService as dependency**

In `RagnarokTerminalRunner`, add the field and constructor injection:

```java
// Add field (after existing fields):
private final SkillService skillService;

// Update constructor signature to include SkillService:
public RagnarokTerminalRunner(BattleService bs, PlayerService ps, ItemService is,
                              PlayerRepository pr, PlayerItemRepository pir,
                              MapMonsterRepository mmr, MonsterRepository mr,
                              PlayerMapper pm, MapPortalRepository portalRepo,
                              SkillService skillService) {
    // ... existing assignments ...
    this.skillService = skillService;
}
```

Add import: `import com.ragnarok.application.service.SkillService;`
Add import: `import com.ragnarok.application.service.SkillRowDTO;`

- [ ] **Step 7.2: Add "S. Skills" option to renderStatusMenu display**

In `renderStatusMenu()`, add after the stat points line (before `"0. Voltar"`):

```java
System.out.println("S. Skills");
```

- [ ] **Step 7.3: Add "S" input branch BEFORE the pontos <= 0 guard**

In `renderStatusMenu()`, the current flow is:
```java
if ("0".equals(input) || input.isEmpty()) return;

if (pontos <= 0) {  // ← "S" must be BEFORE this line
    ...
}
```

Insert the "S" branch between the return-check and the pontos-check:

```java
if ("0".equals(input) || input.isEmpty()) return;

if ("S".equalsIgnoreCase(input)) {
    renderSkillsMenu();
    continue;
}

if (pontos <= 0) {
    System.out.println("Sem pontos para distribuir.");
    continue;
}
```

- [ ] **Step 7.4: Add renderSkillsMenu() method**

Add this new method to `RagnarokTerminalRunner` (after `renderInventoryMenu()`). Note: `import com.ragnarok.application.service.SkillRowDTO` was already added in Step 7.1 — do not duplicate it.

```java
private void renderSkillsMenu() {
    while (true) {
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        int skillPts = p.getSkillPoints() != null ? p.getSkillPoints() : 0;

        List<SkillRowDTO> skills = skillService.listarSkillsDoPlayer(currentPlayer.getId());

        System.out.println("\n=== SKILLS (Skill Points: " + skillPts + ") ===");
        System.out.println("Classe: " + (p.getJobClass() != null ? p.getJobClass().toUpperCase() : "?"));
        System.out.println();

        if (skills.isEmpty()) {
            System.out.println("Nenhuma skill disponivel para sua classe.");
            System.out.println("(Pressione ENTER para voltar)");
            scanner.nextLine();
            return;
        }

        for (int i = 0; i < skills.size(); i++) {
            SkillRowDTO sk = skills.get(i);
            String status;
            if (sk.currentLevel() >= sk.maxLevel()) {
                status = "[MAX]";
            } else if (sk.currentLevel() > 0 && sk.canLearn()) {
                status = "[APRENDIDA]";          // learned and can upgrade
            } else if (sk.currentLevel() > 0) {
                status = "[APRENDIDA - " + sk.blockedReason() + "]"; // learned but blocked from upgrading now
            } else if (!sk.canLearn()) {
                status = "[BLOQUEADA: " + sk.blockedReason() + "]";
            } else {
                status = "[DISPONIVEL]";
            }
            System.out.printf("%-3d [%-20s] %-30s Lv %d/%-3d %s%n",
                    (i + 1), sk.aegisName(), sk.name(), sk.currentLevel(), sk.maxLevel(), status);
        }

        System.out.println("0. Voltar");
        System.out.print("> ");

        String input = scanner.nextLine().trim();

        if ("0".equals(input) || input.isEmpty()) return;

        int escolha;
        try {
            escolha = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Digite apenas numeros.");
            continue;
        }

        if (escolha < 1 || escolha > skills.size()) {
            System.out.println("Opcao invalida.");
            continue;
        }

        SkillRowDTO selecionada = skills.get(escolha - 1);

        if (!selecionada.canLearn()) {
            System.out.println(">>> " + selecionada.blockedReason());
            continue;
        }

        String resultado = skillService.aprenderSkill(currentPlayer.getId(), selecionada.aegisName());
        System.out.println(resultado);
    }
}
```

- [ ] **Step 7.5: Compile check**

```bash
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 7.6: Run full test suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS

- [ ] **Step 7.7: Manual smoke test**

```bash
./mvnw spring-boot:run
```

1. Select `4. Ver Status`
2. Select `S` — Skills menu should open
3. Verify skills list shows for your class
4. If skillPoints > 0: select a DISPONIVEL skill and verify it levels up
5. Select `0` to return to Status, `0` again to main menu

- [ ] **Step 7.8: Commit**

```bash
git add src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java
git commit -m "feat: add skills menu to terminal UI"
```

---

## Done

All three chunks complete. The skills system is fully functional:
- Skills and skill_tree are read from DB
- Player skills persisted in `player_skills` table
- Prerequisite validation with AND-logic across job classes
- Terminal menu accessible via `S` from the Status screen
