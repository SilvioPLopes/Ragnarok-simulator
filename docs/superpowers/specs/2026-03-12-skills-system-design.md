# Skills System Design

**Date:** 2026-03-12
**Branch:** feature/Alt-02
**Status:** Approved

---

## Context

The game already has two populated tables (`skills`, `skill_tree`) and a `skillPoints` field on `PlayerEntity`. This spec covers the infrastructure and application code needed to make skills functional in the terminal UI.

---

## Data Layer

### New Entities

**`SkillEntity`** (`infrastructure/persistence/`)
- Maps table `skills`
- Fields: `id` (BIGINT), `aegisName` (VARCHAR), `name` (VARCHAR), `type` (VARCHAR)

**`SkillTreeEntity`** (`infrastructure/persistence/`)
- Maps table `skill_tree` — read-only (no save() calls expected)
- `id` mapped with `@GeneratedValue(strategy = IDENTITY)` (sequence-backed in DB)
- Fields: `id` (INT), `jobClass` (VARCHAR), `skillId` (VARCHAR — references `skills.aegis_name`), `maxLevel` (INT), `prereqSkill` (VARCHAR, nullable), `prereqLevel` (INT, nullable)
- **One skill can have multiple rows** (one per prerequisite). Example: `KN_BOWLINGBASH` has 5 rows, one per required prereq skill. The repository returns all rows for a job class; `SkillService` groups them by `skillId`.

**`PlayerSkillEntity`** (`infrastructure/persistence/`)
- Maps table `player_skills` (created by JPA `ddl-auto=update`)
- Fields: `id` (UUID), `playerId` (Long FK → players), `skillId` (VARCHAR — aegis_name), `currentLevel` (INT)
- `@UniqueConstraint` on `(playerId, skillId)` to prevent duplicate rows

### New Repositories

| Repository | Key queries |
|---|---|
| `SkillRepository` | `findByAegisName(String)` |
| `SkillTreeRepository` | `findByJobClassIgnoreCase(String)` — Spring Data derived query handles case-insensitive match, avoiding `UPPER()` |
| `PlayerSkillRepository` | `findByPlayerId(Long)`, `findByPlayerIdAndSkillId(Long, String)` |

No manual SQL migrations needed — `ddl-auto=update` handles `player_skills` creation.

#### job_class matching note

`skill_tree.job_class` uses mixed case (`"Novice"`, `"Swordman"`) while `PlayerEntity.jobClass` stores the Java enum name (`"NOVICE"`, `"SWORDSMAN"`). For the 6 current playable classes, case-insensitive match is sufficient. **Known exception:** DB has `"Swordman"` (one S) vs enum `"SWORDSMAN"` (two S) — this is a spelling mismatch. For now Swordsman is not a playable class in the game, so it is out of scope. When job-change is implemented, a mapping table or normalization will be needed.

---

## Application Service

**`SkillService`** (`application/service/`)

**Required dependencies:** `PlayerRepository`, `SkillTreeRepository`, `PlayerSkillRepository`

### `SkillRowDTO` — public record

`SkillRowDTO` must be a **public** top-level record (or public static inner record) in the `application/service/` package so that `RagnarokTerminalRunner` (in `runner/`) can reference it.

Fields:
- `aegisName`, `name`, `maxLevel`, `currentLevel` (0 if not yet learned)
- `canLearn` (boolean): ALL prereqs met AND currentLevel < maxLevel AND skillPoints > 0
- `blockedReason` (String, nullable): human-readable message when `canLearn = false`
  - Examples: `"Requer AL_HEAL Lv3"`, `"Nivel maximo atingido"`, `"Sem Skill Points"`

### `listarSkillsDoPlayer(Long playerId) → List<SkillRowDTO>`

Steps:
1. Load player from DB; get `jobClass` and `skillPoints` (null-safe: treat null as 0)
2. Load all `SkillTreeEntity` rows for `jobClass` via `findByJobClassIgnoreCase()`
3. Group rows by `skillId` — each group represents one skill with its set of prerequisites
4. For each skill group, build a `SkillRowDTO`:
   - Look up `currentLevel` from `PlayerSkillRepository.findByPlayerIdAndSkillId()` (no job class filter — prerequisites can belong to parent job classes)
   - Check ALL prereq rows: for each row where `prereqSkill` is not null, look up `findByPlayerIdAndSkillId(playerId, prereqSkill)` and verify `currentLevel >= prereqLevel`
   - Set `canLearn` and `blockedReason` accordingly
5. Return list sorted by `skillId` (alphabetical, stable order)
6. If the list is empty (no skills for job class), return empty list — terminal prints `"Nenhuma skill disponivel para sua classe."`

### `aprenderSkill(Long playerId, String aegisName) → String`

Must be `@Transactional`.

Validation chain (returns error message on first failure):
1. Skill exists in skill_tree for player's job class (`findByJobClassIgnoreCase`)
2. **All** prerequisite rows satisfied — query `PlayerSkillRepository.findByPlayerIdAndSkillId(playerId, prereqSkill)` for each prereq row (no job class filter)
3. Current level < max level
4. `skillPoints > 0` (null-safe: treat null skillPoints as 0)

On success:
- Upsert `PlayerSkillEntity`: if exists, increment `currentLevel`; else create new with `currentLevel = 1`
- Decrement `player.skillPoints` by 1 (re-read player from DB inside transaction before decrementing)
- Return success message: `">>> [SkillName] subiu para Lv X!"`

---

## Terminal UI

**Entry point:** Menu de Status adds option `"S. Skills"` (letter input). The existing `renderStatusMenu()` input handler has a `pontos <= 0` guard that prints "Sem pontos para distribuir" before reaching the parseInt block. The `"S"` branch must be placed **before** the `pontos <= 0` guard — Skills must be accessible regardless of whether `statPoints > 0`.

```java
if ("0".equals(input) || input.isEmpty()) return;
if ("S".equalsIgnoreCase(input)) { renderSkillsMenu(); continue; }  // ← BEFORE pontos check
if (pontos <= 0) { ... }
parseInt(input) → stat distribution
```

**Skills menu layout:**
```
=== SKILLS (Skill Points: 3) ===
Classe: ACOLYTE

1. [AL_HEAL] Heal          Lv 2/10  [APRENDIDA]
2. [AL_INCAGI] Inc Agi     Lv 0/10  [BLOQUEADA: Requer AL_HEAL Lv3]
3. [AL_BLESSING] Blessing  Lv 1/10  [APRENDIDA]
4. [AL_CURE] Cure          Lv 0/4   [DISPONIVEL]
0. Voltar
>
```

- Player types the number of the skill to learn/level up
- If blocked: print the `blockedReason` and wait for next input (no skillPoint spent)
- If no skillPoints: print `"Sem Skill Points disponíveis."` and return to menu
- After learning: print the success message and refresh the list

---

## Key Decisions

- `job_class` matching uses `findByJobClassIgnoreCase()` (Spring Data derived query) — case-insensitive without `UPPER()` in JPQL
- Spelling mismatch `SWORDSMAN` vs `Swordman` is out of scope (Swordsman not playable yet)
- One skill = multiple `SkillTreeEntity` rows (one per prereq); `SkillService` groups by `skillId`
- Prerequisite lookup uses `PlayerSkillRepository` without job class filter (cross-class prereqs work naturally)
- `SkillRowDTO` is `public` so `runner/` package can access it
- `aprenderSkill` is `@Transactional` (two writes must be atomic)
- `skillPoints` null treated as 0 everywhere (consistent with `statPoints` pattern in terminal)
- 1 skill point = 1 level, always
- Blocked skills are shown with reason, not hidden
- `player_skills` auto-created by JPA (consistent with project pattern)

---

## Out of Scope

- Skill effects in battle (passive/active buffs)
- Skill casting during combat
- Job class expansion beyond current 6 in the enum
- `SWORDSMAN`/`Swordman` spelling normalization (needed when job-change is implemented)
