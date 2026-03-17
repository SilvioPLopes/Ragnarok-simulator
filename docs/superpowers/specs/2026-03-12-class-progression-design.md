# Design: Class Progression System

**Date:** 2026-03-12
**Branch:** feature/Alt-02
**Status:** Approved

---

## Overview

Implement two related features to enable class progression for the player:

1. **Job Level Cap validation** in `LevelingService` — stop awarding job exp/levels once the class maximum is reached.
2. **NPC de Troca de Classe** in the terminal — allow the player to change job class when requirements are met in Prontera.

---

## Context

The `JobClass` enum was refactored (prior to this spec) to include:
- `tier` (int): 0 = Novice/Doram, 1 = Class 1, 2 = Class 2, 3 = Transcendent, 4 = Class 4
- `parentClass` (JobClass): the class this one evolves from (null for tier 0 and tier 1)
- `maxJobLevel()`: returns `9` for tier 0, `50` for all others
- `nextClasses()`: returns all `JobClass` entries whose `parentClass == this`

**Special cases:**
- `SUPER_NOVICE` and `SUMMONER` are tier 0 — they are starting classes, not evolutions. They do NOT have a standard Class 1 progression path.
- Tier 1 classes have `parentClass = null` (any `NOVICE` can choose any Class 1 freely).
- `SUMMONER` has no `nextClasses()` — no class change path defined yet.

---

## Feature 1: Job Level Cap in `LevelingService`

### Rules

| Condition | Behavior |
|---|---|
| `baseLevel >= 99` | Do NOT add base exp to `player.baseExp`; log `(base nível máximo atingido)` |
| `jobLevel >= jobClass.maxJobLevel()` | Do NOT add job exp to `player.jobExp`; log `(job nível máximo atingido)` |

### Implementation

**`LevelingService.processarExperiencia(Player, long, long)`:**

**Base exp block:**
- Before adding base exp: resolve `JobClass` from `player.getJobClass()` (see guard below).
- If `player.getBaseLevel() >= 99`: do NOT add `gainedBaseExp` to `player.baseExp`. Log `(base nível máximo atingido)`. Skip the base level-up `while` entirely.
- Otherwise: add exp and run the existing `while` loop (already handles overflow correctly). Add guard `player.getBaseLevel() < 99` to the `while` condition.

**Job exp block:**
- Resolve `maxJobLevel` from `JobClass`.
- If `player.getJobLevel() >= maxJobLevel`: do NOT add `gainedJobExp` to `player.jobExp`. `player.jobExp` remains unchanged. Log `(job nível máximo atingido)`. Skip the job level-up `while` entirely.
- Otherwise: add exp and run the existing `while` loop. Add guard `player.getJobLevel() < maxJobLevel` to the `while` condition to prevent overshooting.

**Resolving `JobClass` from String (defensive guard):**
```java
JobClass jobClass;
try {
    jobClass = JobClass.valueOf(player.getJobClass());
} catch (IllegalArgumentException | NullPointerException e) {
    throw new IllegalStateException("JobClass inválida ou não definida: " + player.getJobClass());
}
```
This guard is applied once at the start of `processarExperiencia`.

**No new classes needed.** All changes are within `LevelingService`.

---

## Feature 2: ClassChangeService (Application Layer)

### New class: `com.ragnarok.application.service.ClassChangeService`

**Methods:**
```java
List<JobClass> listarClassesDisponiveis(Long playerId)
void trocarClasse(Long playerId, JobClass novaClasse)
```

`listarClassesDisponiveis` applies the same guard logic (step 3 below) and returns the filtered list of valid target `JobClass` values. Returns empty list if the player's class has no progression path. Used by `renderNpcMenu()` to display options without bypassing the service layer. **Job level requirements are NOT checked by this method; they are enforced exclusively by `trocarClasse`.**

**Validation steps (in order):**

1. Load `PlayerEntity` by id — throw `IllegalStateException("Jogador não encontrado.")` if not found.
2. Parse current `jobClass` string to `JobClass` enum using the same defensive guard as `LevelingService`. Throw `IllegalStateException` on invalid value.
3. **Guard: only `NOVICE` (tier 0) and Tier 1 classes can change class.** If `currentJobClass.tier >= 2` OR (`currentJobClass.tier == 0` AND `currentJobClass != JobClass.NOVICE`): throw `IllegalStateException("Troca de classe não disponível para esta classe.")`. This blocks `SUPER_NOVICE` and `SUMMONER` from the standard progression path.
4. Build the set of DB-valid class names: `Set<String> dbClasses` from `skillTreeRepository.findDistinctJobClasses()`, normalized to uppercase.
5. Determine valid target classes:
   - If `currentJobClass == JobClass.NOVICE`: valid targets = all `JobClass` values with `tier == 1` whose `name()` is in `dbClasses`.
   - If `currentJobClass.tier == 1`: valid targets = `currentJobClass.nextClasses()` filtered by presence in `dbClasses`.
6. If `novaClasse` not in valid targets → throw `IllegalStateException("Classe inválida para progressão.")`.
7. Check job level requirement:
   - `currentJobClass == JobClass.NOVICE` (tier 0 → tier 1): require `jobLevel >= 9`, else throw `IllegalStateException("Job level insuficiente. Necessário: 9")`.
   - `currentJobClass.tier == 1` (tier 1 → tier 2): require `jobLevel >= 40`, else throw `IllegalStateException("Job level insuficiente. Necessário: 40")`.
   - Any other combination: throw `IllegalStateException("Progressão de classe não suportada para este tier.")` (defensive guard, should not be reachable after step 3).
8. Apply transition:
   - `playerEntity.setJobClass(novaClasse.name())`
   - `playerEntity.setJobLevel(1)`
   - `playerEntity.setJobExp(0L)`
   - `skillPoints` unchanged (accumulated points are kept)
9. Save `PlayerEntity`.

**Dependencies:** `PlayerRepository`, `SkillTreeRepository`.

### SkillTreeRepository — new query

```java
@Query("SELECT DISTINCT UPPER(s.jobClass) FROM SkillTreeEntity s")
List<String> findDistinctJobClasses();
```

Returns uppercased strings, safe for direct comparison with `JobClass.name()` (which is always uppercase). Used to filter which classes have skill data in the DB before presenting them to the player.

---

## Feature 3: Terminal NPC (RagnarokTerminalRunner)

### Exploration menu change

Option `6. Falar com NPC` is shown **only when `mapName.equals("prontera")`**.

```
[MAPA: prontera] (HP: 100/100)
1. Caçar monstros
2. Portais
3. Inventário
4. Ver Status
5. Sair
6. Falar com NPC
```

### New method: `renderNpcMenu()`

Flow:
1. Call `classChangeService.listarClassesDisponiveis(playerId)` to get the valid target list.
2. If list is empty: print `"O NPC diz: Troca de classe não disponível para sua classe atual."` and return to exploration menu.
3. Load `PlayerEntity` to display current job level. Determine required job level: `9` if current class is `NOVICE`, `40` otherwise.
4. Print required job level and current job level.
5. Display numbered list of available classes with `descricao`.
6. Read player choice (0 = cancel). Return to exploration menu on cancel.
7. Call `classChangeService.trocarClasse(playerId, escolha)`.
8. Catch `IllegalStateException` and display the message (stays in NPC menu for retry).
9. On success: print `">>> Você se tornou um(a) [Classe.descricao]!"` and return to exploration menu.

---

## Data Flow

```
Terminal input
    │
    ▼
RagnarokTerminalRunner.renderNpcMenu()
    │
    ├── ClassChangeService.listarClassesDisponiveis(playerId)
    │       ├── PlayerRepository.findById()
    │       └── SkillTreeRepository.findDistinctJobClasses()  ← UPPER-cased strings
    │
    └── ClassChangeService.trocarClasse(playerId, novaClasse)
            ├── PlayerRepository.findById()
            ├── SkillTreeRepository.findDistinctJobClasses()
            └── PlayerRepository.save()
                    │
                    ▼
        PlayerEntity updated (jobClass=novaClasse.name(), jobLevel=1, jobExp=0, skillPoints unchanged)
```

---

## Error Handling

All validation errors throw `IllegalStateException` with a human-readable PT-BR message. The terminal catches them and prints the message without crashing the game loop.

---

## Testing

**`LevelingServiceTest` (unit):**
- Player at base level 99 receives exp: assert `player.baseExp` is NOT incremented, `baseLevel` stays 99.
- Player below level 99 levels up normally to 99: assert stops at 99 even with large exp gain.
- Player at `maxJobLevel()` receives job exp: assert `player.jobExp` is NOT incremented, `jobLevel` unchanged.
- Player at job level cap-minus-1 receives enough exp to overshoot: assert caps at `maxJobLevel()`, no further level-up.
- `player.jobClass` is null: assert `IllegalStateException` thrown.

**`ClassChangeServiceTest` (unit, mocked repos):**
- `listarClassesDisponiveis` for NOVICE returns only tier-1 classes present in mocked DB set.
- `listarClassesDisponiveis` for KNIGHT (tier 2) returns empty list.
- `listarClassesDisponiveis` for SUPER_NOVICE returns empty list.
- Valid Novice → Swordsman with `jobLevel == 9`: assert transition applied, `jobLevel=1`, `jobExp=0`, `skillPoints` preserved.
- Novice with `jobLevel == 8`: assert `IllegalStateException("Job level insuficiente. Necessário: 9")`.
- Class 1 → Class 2 with `jobLevel == 40`: assert transition applied.
- Class 1 → Class 2 with `jobLevel == 39`: assert `IllegalStateException`.
- Player is `KNIGHT` (tier 2): assert `IllegalStateException("Troca de classe não disponível para esta classe.")`.
- Player is `SUPER_NOVICE` (tier 0, not NOVICE): assert `IllegalStateException("Troca de classe não disponível para esta classe.")`.
- Player is `SUMMONER` (tier 0, not NOVICE): assert `IllegalStateException`.
- `novaClasse` not in valid targets: assert `IllegalStateException("Classe inválida para progressão.")`.
- `playerEntity.jobClass` is null or invalid string: assert `IllegalStateException`.

**`ClassChangeIntegrationTest` (Spring Boot test, real DB):**
- Full Novice → Swordsman flow: assert `playerEntity.jobClass == "SWORDSMAN"`, `jobLevel == 1`, `jobExp == 0`, `skillPoints` unchanged in DB.
- **Pre-condition:** the `skill_tree` table must have at least one row with `job_class = 'swordsman'` (or uppercase equivalent). Use `@Sql` to insert the row if the DB may be empty.

---

## Out of Scope

- Tier 3 (Transcendent) and Tier 4 class changes — not implemented in this iteration.
- `SUMMONER` class change path — Doram has no evolution defined yet.
- `SUPER_NOVICE` evolution path — special case, deferred.
- Gender-locked classes (BARD/DANCER) — no gender restriction implemented.
