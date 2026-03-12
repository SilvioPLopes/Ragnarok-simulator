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
- `SUPER_NOVICE` and `SUMMONER` are tier 0 — they are starting classes, not evolutions.
- Tier 1 classes have `parentClass = null` (any Novice can choose any Class 1 freely).
- `SUMMONER` has no `nextClasses()` — no class change path defined yet.

---

## Feature 1: Job Level Cap in `LevelingService`

### Rules

| Condition | Behavior |
|---|---|
| `baseLevel >= 99` | Stop awarding base level ups; discard excess base exp |
| `jobLevel >= jobClass.maxJobLevel()` | Stop awarding job exp and job level ups |

### Implementation

**`LevelingService.processarExperiencia(Player, long, long)`:**

- Before adding base exp: if `player.baseLevel >= 99`, skip base exp entirely.
- The base `while` loop already handles overflow correctly — add a `baseLevel < 99` guard.
- Before adding job exp: if `player.jobLevel >= maxJobLevel`, skip job exp. Log `(job nível máximo atingido)` instead of `(+X Job XP)`.
- `maxJobLevel` is resolved by parsing `player.jobClass` (String) to `JobClass` enum via `JobClass.valueOf(player.getJobClass())`.

**No new classes needed.** All changes are within `LevelingService`.

---

## Feature 2: ClassChangeService (Application Layer)

### New class: `com.ragnarok.application.service.ClassChangeService`

**Method:**
```java
void trocarClasse(Long playerId, JobClass novaClasse)
```

**Validation steps (in order):**
1. Load `PlayerEntity` by id — throw if not found.
2. Parse current `jobClass` string to `JobClass` enum.
3. Determine valid target classes:
   - If current tier == 0 (Novice): valid targets = all tier-1 `JobClass` values present in `skill_tree`.
   - If current tier == 1: valid targets = `currentJobClass.nextClasses()` filtered by presence in `skill_tree`.
4. If `novaClasse` not in valid targets → throw `IllegalStateException("Classe inválida para progressão.")`.
5. Check job level requirement:
   - Tier 0 → Tier 1: `jobLevel >= 9`
   - Tier 1 → Tier 2: `jobLevel >= 40`
   - Else → throw `IllegalStateException("Job level insuficiente. Necessário: X")`.
6. Apply transition:
   - `playerEntity.setJobClass(novaClasse.name())`
   - `playerEntity.setJobLevel(1)`
   - `playerEntity.setJobExp(0L)`
   - `skillPoints` unchanged (accumulated points are kept)
7. Save `PlayerEntity`.

**Dependencies:** `PlayerRepository`, `SkillTreeRepository`.

### SkillTreeRepository — new query

```java
@Query("SELECT DISTINCT s.jobClass FROM SkillTreeEntity s")
List<String> findDistinctJobClasses();
```

Used to filter which classes actually have skill data in the DB before presenting them to the player.

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
1. Load player, parse `jobClass`, get `tier`.
2. If tier >= 2: print `"O NPC diz: Você já atingiu uma classe avançada."` and return.
3. Build list of available classes:
   - Tier 0: all tier-1 `JobClass` values whose name exists in `skillTreeRepo.findDistinctJobClasses()`.
   - Tier 1: `currentJobClass.nextClasses()` filtered by `findDistinctJobClasses()`.
4. If list is empty: print `"Nenhuma classe disponível no momento."` and return.
5. Print job level requirement (9 or 40) and current job level.
6. Display numbered list of available classes with `descricao`.
7. Read player choice (0 = cancel).
8. Call `classChangeService.trocarClasse(playerId, escolha)`.
9. Catch `IllegalStateException` and display the message.
10. On success: print `">>> Você se tornou um(a) [Classe.descricao]!"`.

---

## Data Flow

```
Terminal input
    │
    ▼
RagnarokTerminalRunner.renderNpcMenu()
    │
    ▼
ClassChangeService.trocarClasse(playerId, novaClasse)
    │  ├── PlayerRepository.findById()
    │  ├── SkillTreeRepository.findDistinctJobClasses()
    │  └── PlayerRepository.save()
    │
    ▼
PlayerEntity updated (jobClass, jobLevel=1, jobExp=0)
```

---

## Error Handling

All validation errors throw `IllegalStateException` with a human-readable PT-BR message. The terminal catches them and prints the message, returning to the NPC menu.

---

## Testing

- `LevelingServiceTest`: assert base exp stops at level 99; assert job exp stops at `maxJobLevel()`; assert job exp ignored when at cap.
- `ClassChangeServiceTest` (unit): mock repos; test valid transition, invalid class, insufficient job level, tier >= 2 guard.
- `ClassChangeIntegrationTest`: use real DB; test full Novice → Swordsman flow.

---

## Out of Scope

- Tier 3 (Transcendent) and Tier 4 class changes — not implemented in this iteration.
- `SUMMONER` class change path — Doram has no evolution defined yet.
- `SUPER_NOVICE` evolution path — special case, deferred.
- Gender-locked classes (BARD/DANCER) — no gender restriction implemented.
