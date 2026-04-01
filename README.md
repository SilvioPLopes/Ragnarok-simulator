# Ragnarok Core — Hexagonal Architecture

![CI](https://github.com/SilvioPLopes/ragnarok-core/actions/workflows/ci.yml/badge.svg)
![Coverage](https://img.shields.io/badge/coverage-85%25%2B-brightgreen)
![Tests](https://img.shields.io/badge/tests-286%20passing-brightgreen)

The core engine of a Ragnarok Online emulation and data-management system built on **Hexagonal Architecture (Ports and Adapters)**. All game data (monsters, items, maps, warps, drops, skills) is imported directly from the official **rAthena** server (`db/re/` — Renewal version).

---

## Project Status

| System | Status | Details |
|---|---|---|
| Hexagonal Architecture | Operational | Domain / Application / Infrastructure / Runner |
| rAthena Data | Operational | 2675 monsters, all items, 1864 warps, 2374 spawns, 12544 drops |
| Map Navigation | Operational | Real rAthena portals, weighted monster encounters by `amount` |
| Battle Engine | Operational | Physical damage, monster counter-attack, elemental and size modifiers |
| Loot System | Operational | Real rAthena drop rates (0–100%), RNG, inventory persistence |
| Leveling System | Operational | Base/Job XP, auto Level Up, Full Heal, stat/skill points |
| Stat Distribution | Operational | Terminal menu for spending `statPoints` (STR/AGI/VIT/INT/DEX/LUK) |
| Skill System | Operational | Full tree, AND-logic prerequisites, learning with `skillPoints` |
| Class Chain | Operational | Skills from ALL previous classes visible and learnable |
| Skill Effects | Operational | Buffs, passives, formula-based damage, SP cost, duration in turns |
| Size Modifiers | Operational | Weapon vs. Small/Medium/Large with real game table |
| Class Change | Operational | NOVICE → Tier1 → Tier2 → Tier3 with job level validation |
| Auto Startup | Operational | `StartupDataLoader` populates all static tables on boot |
| Schema Management | Operational | Flyway V1 migration; Hibernate validates on startup |
| JWT Authentication | Operational | `JwtFilter` (`@Order(1)`) validates `Authorization: Bearer` on all `/api/*` routes; public paths: `/api/accounts/register`, `/api/accounts/login` |
| CORS Configuration | Operational | `FilterRegistrationBean<CorsFilter>` at `HIGHEST_PRECEDENCE` — allows `http://localhost:3000` (all methods/headers) |
| Account System | Operational | `AccountController`: register + login returning `{token, accountId}`; `AccountService.LoginResult` record |
| Player Ownership | Operational | `accountId` stored on player creation; `GET /api/players` filters by JWT owner; `validateOwnership` guards all player routes |
| Antifraude Integration | Operational | `FraudClient` calls `POST http://localhost:8081/api/fraud/analyze`; Resilience4j CircuitBreaker — fail-open returns `APPROVED` if antifraude is down |
| Resilience4j | Operational | `@Retry` (3 attempts, 2 s exponential backoff) + `@CircuitBreaker` on antifraude calls |
| Spring Cache (Caffeine) | Operational | `weaponSizeModifiers`, `skillBuffEffects`, `skillTree`, `playerSkills` — zero repeated queries in battle |
| REST API + Swagger UI | Operational | Full game loop via browser: Players, Battle, Skills, Inventory, Map, Auth — `GET /swagger-ui.html` |
| Spring Events | Operational | `BattleService` + `SkillCombatService` publish `MonsterKilledEvent` / `PlayerDiedEvent`; `BattleEventHandler` handles loot, XP, resurrection |
| Testcontainers | Operational | All integration tests use an ephemeral PostgreSQL container — no local DB required for `./mvnw test` |
| Test Coverage | **286+ tests** | Unit + Integration — zero failures (JaCoCo ≥ 85% line / ≥ 62% branch) |

---

## Architecture & Organization

The project strictly follows the separation of concerns of hexagonal architecture:

### 1. Domain (`com.ragnarok.domain`)

**The heart of the system.** Contains pure business logic and models.

- **Golden Rule:** No framework dependencies. No `@Entity`, `@Table`, or any Spring/Hibernate annotations.
- Uses strong types (`Integer`, `Double`) for safe mathematical calculations.
- `BattleEngine` and `LevelingService` are pure services — testable without a database.

### 2. Application (`com.ragnarok.application`)

**The orchestration layer (Use Cases).** Receives commands, fetches data through ports, and coordinates flow.

| Service | Responsibility |
|---|---|
| `BattleService` | Combat turn: damage, counter-attack, weapon-size modifier; publishes `MonsterKilledEvent` / `PlayerDiedEvent` |
| `BattleEventHandler` | `@EventListener`: persists loot, processes XP via `LevelingService`, publishes `PlayerLeveledUpEvent`, resurrects player |
| `SkillService` | Class-chain listing, prerequisite validation, and skill learning |
| `SkillCombatService` | Skill usage in combat: HEAL, BUFF, PHYSICAL_DAMAGE, MAGICAL_DAMAGE |
| `ClassChangeService` | Class progression with job level validation |
| `ItemService` | Inventory management, equip/unequip, auto slot-swap |
| `MapService` | Current map info, portal listing, walk (random encounter), and travel between maps |
| `PlayerService` | Character creation and management |
| `MonsterCatalogService` | Monster ETL via external API |
| `WeaponSizeService` | Size modifier lookup by weapon type and monster size (`@Cacheable`) |
| `ScriptInterpreter` | Formula evaluation engine (`"ATK * skill_lv * 1.3"`) |

### 3. REST API (`com.ragnarok.api`)

Exposes the full game loop as a REST API with OpenAPI documentation via **springdoc-openapi**.

| Controller | Endpoints |
|---|---|
| `AccountController` | `POST /api/accounts/register`, `POST /api/accounts/login` (returns `{token, accountId}`) |
| `PlayerController` | `GET /api/players`, `GET /api/players/{id}`, `POST /api/players`, `PUT /{id}/stats`, `POST /{id}/resurrect`, `GET /{id}/class-change`, `POST /{id}/class-change` |
| `BattleController` | `POST /api/battle/attack` |
| `SkillController` | `GET /api/players/{id}/skills`, `POST .../learn`, `POST .../use` |
| `ItemController` | `GET /api/players/{id}/inventory`, `POST .../inventory/{itemId}/use`, `POST .../inventory/{itemId}/equip` |
| `MapController` | `GET /api/players/{id}/map`, `GET /api/maps/{mapId}/portals`, `POST .../walk`, `POST .../travel` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`: maps domain exceptions to HTTP 400/404/500 |

**Swagger UI:** `http://localhost:8080/swagger-ui.html` — interactive docs for all groups without cloning the repo.

> All routes under `/api/*` require `Authorization: Bearer <token>` except `/api/accounts/register` and `/api/accounts/login`.

### 4. Infrastructure (`com.ragnarok.infrastructure`)

**Adapters to the outside world.**

- **Client:** Communication with the Ragnapi API. Uses immutable DTOs (Records) with `String` fields to tolerate malformed data.
- **Persistence:** PostgreSQL communication via JPA. Entities use `@Entity` with a Flattening strategy.
- **Mapper:** Translator bridge. Converts `DTO → Domain → Entity`, applying sanitization and safe unboxing.
- **`BuffSerializer`:** Serializes/deserializes the `ActiveBuff` list as JSON in the player's `active_buffs_json` column.

### 5. Runner (`com.ragnarok.runner`)

| Class | Order | Function |
|---|---|---|
| `RathenaImporter` | `@Order(1)` | Imports monsters and items from **local classpath** YAML files (`src/main/resources/rathena/`); threshold check: ≥2600 monsters / ≥25000 items before skipping re-import |
| `PlayerSeedLoader` | `@Order(2)` | Creates the initial player |
| `StartupDataLoader` | `@Order(3)` | Populates `maps`, `map_portals`, `map_monsters`, `monster_drops`, `skills`, `skill_tree`, `skill_buff_effects`, and `weapon_size_modifiers` from SQLs in `src/main/resources/db/` |
| `RagnarokTerminalRunner` | — | Terminal UI, exploration and combat game loop |

---

## Data Flows

### 1. Monster Catalog (ETL & Data Mining)

Resilient flow that loads external data, sanitizes inconsistencies, and performs relational data mining.

1. **Trigger:** `MonsterCatalogService.carregarESalvarMonstro(Long id)`
2. **Fetch (Client):** `RagnapiClient` consumes external API — input: "dirty" JSON (`"hp": "10,000"`, `"name": "scorpion"`)
3. **Sanitization (Mapper):** `MonsterMapper.toDomain(dto)` — removes commas, normalizes strings, ensures strong typing (`String → Integer`)
4. **Domain:** Instantiation of pure `Monster` object for safe calculations
5. **Persistence (Mapper):** `MonsterMapper.toEntity(domain)` applies **Flattening** (flattens nested objects into flat columns)
6. **Automatic Mining:** Drops identified; non-existent items become placeholders. Spawns populate `maps` and `map_monsters`

### 2. Character Creation (Factory & Persistence)

1. **Trigger:** `PlayerService.criarNovoPersonagem(name, class)`
2. **Domain:** Level 1, HP 100/100, location: Prontera
3. **Mapping:** `PlayerMapper` converts `PlayerStats`, `PlayerLocation` to flat columns
4. **Persistence:** Transactional commit to PostgreSQL

### 3. Item Management (Inventory & Equipment)

1. **UUID Inventory:** Each item has a unique UUID in `player_items`, supporting multiple instances of the same item
2. **Auto-Swap:** `ItemService` identifies the `EquipSlot` of the new item, removes the equipped item in the same slot, and equips the new one in a single atomic transaction
3. **Persisted Loot:** Drop rate (`rate`) stored at 0–100 scale (normalized from rAthena's 0–10000 scale during SQL ingestion)

### 4. Battle & Progression (Engine & Leveling)

1. **Trigger:** `BattleService.realizarAtaque(playerId, monsterId)`
2. **Combat (Engine):** `BattleEngine` calculates `(STR*2 + WeaponATK) - EnemyDEF`; applies elemental and weapon-size modifier
3. **Counter-attack:** Monster responds in the same turn with `ATK - PlayerDEF`
4. **Loot (RNG):** On monster death, `nextDouble(0, 100) < rate` determines each drop; items saved in `player_items`
5. **XP & Level Up:** `LevelingService` processes base/job XP, checks curve (`Level * 100`), applies Full Heal, +5 stat points, +1 skill point per level up

### 5. World Navigation (Map & Portals)

1. **Trigger:** Player selects "Portais" in the exploration menu
2. **Query:** `MapPortalRepository.findDestinosByMapFrom(currentMap)` returns available destinations
3. **Travel:** `PlayerEntity.mapName` updated and persisted
4. **Encounter:** Monster drawn by weight proportional to `amount` in `map_monsters`
5. **Death:** Player revives in `prontera`, `mapName` reset

### 6. Skills System (Learning, Prerequisites, and Class Chain)

1. **Trigger:** Status Menu → key `S`
2. **Class Chain:** `SkillService.resolveClassChain` traverses the `parentClass` field of the `JobClass` enum and returns all classes in the progression. E.g.: `LORD_KNIGHT → [LORD_KNIGHT, KNIGHT, SWORDSMAN]`
3. **Listing:** `listarSkillsDoPlayer` queries `skill_tree` for **the entire chain** — a LORD_KNIGHT sees and can learn `LK_*`, `KN_*`, and `SM_*` skills
4. **AND-logic Prerequisites:** A skill with multiple rows in `skill_tree` requires **all** prerequisites satisfied
5. **Learning:** `aprenderSkill` validates class/chain, prerequisites, max level, and `skillPoints`. Upserts in `player_skills` and decrements `skillPoints`
6. **Immediate Passives:** When learning a `PASSIVE` skill, bonuses are applied instantly via `ActiveBuff` with `durationTurns = -1` (permanent)

### 7. Skill Effects System (Buffs, Passives, and Damage)

1. **Dynamic Formulas:** `ScriptInterpreter.evaluateFormula` evaluates expressions like `"ATK * skill_lv * 1.3"` or `"skill_lv * 2"` at runtime
2. **BUFF:** When using the skill, effects are read from `skill_buff_effects`, calculated, and applied as `ActiveBuff` with duration. Response shows concrete values: `"SM_ENDURE (Lv1). Effect lasts 7 turns. [DEF +2, M_DEF +4]"`
3. **PASSIVE:** Applied on learning. Permanent bonus stored in player's `active_buffs_json`
4. **PHYSICAL_DAMAGE / MAGICAL_DAMAGE:** Formula evaluated with player stats; elemental and weapon-size modifiers applied
5. **HEAL:** Formula evaluated to restore player HP
6. **Size Modifier:** `WeaponSizeService` queries `weapon_size_modifiers` for the damage percentage by weapon × monster size (Small/Medium/Large)

### 8. Spring Events — Decoupled Battle Pipeline

`BattleService` publishes domain events via `ApplicationEventPublisher` instead of calling other services directly. All side-effects of combat are handled asynchronously (but synchronously in the same transaction) by `BattleEventHandler`.

```
realizarAtaque()
  └─ monster HP ≤ 0
       └─ battleEngine.calculateLoot(monster)
       └─ eventPublisher.publishEvent(MonsterKilledEvent)   ← same TX
            └─ BattleEventHandler.onMonsterKilled()
                 ├─ persist loot → player_items
                 ├─ levelingService.processarExperiencia()
                 ├─ save PlayerEntity (base/job level, XP, stat/skill points)
                 └─ if leveled up → publishEvent(PlayerLeveledUpEvent)

  └─ player HP ≤ 0
       └─ eventPublisher.publishEvent(PlayerDiedEvent)       ← same TX
            └─ BattleEventHandler.onPlayerDied()
                 ├─ hpCurrent = hpMax
                 └─ mapName = "prontera"
```

**Domain events:**

| Event | Fields | Publisher | Handler |
|---|---|---|---|
| `MonsterKilledEvent` | `playerId`, `monsterId`, `List<Item> loot`, `baseExp`, `jobExp` | `BattleService` | `BattleEventHandler.onMonsterKilled` |
| `PlayerDiedEvent` | `playerId` | `BattleService` | `BattleEventHandler.onPlayerDied` |
| `PlayerLeveledUpEvent` | `playerId`, `newBaseLevel`, `newJobLevel` | `BattleEventHandler` | — (logged) |

### 9. Safety & Resilience (Null Safety)

1. **Shielded Mapper:** `PlayerMapper` implements Safe Unboxing — `NULL` in numeric fields (XP, Points, Zenny) is converted to `0` before instantiating the Domain, preventing `NullPointerException`
2. **Safe Drop Rate:** `MonsterMapper.mapDrop` treats `rate = null` as `0.0` — item never drops accidentally
3. **Double WHERE EXISTS:** `monster_drops.sql` and `map_monsters.sql` use `WHERE EXISTS` to validate FKs before inserting, guaranteeing zero violations even with a partially populated database

---

## Database

**URL:** `jdbc:postgresql://localhost:5432/ragnarok_db`
**User:** `postgres` / **Password:** *(environment variable `DB_PASS`, local default: `postgre`)*

Schema is managed by **Flyway** (`db/migration/V1__initial_schema.sql`). Hibernate validates on startup.

| Table | Source | Description |
|---|---|---|
| `monsters` | RathenaImporter (startup) | 2675 monsters from `db/re/mob_db.yml` |
| `items` | RathenaImporter (startup) | Items from `db/re/item_db_usable/equip/etc.yml` |
| `maps` | `maps.sql` | All maps from `db/map_index.txt` |
| `map_portals` | `map_portals_v2.sql` | 1864 warps from `npc/re/warps/` |
| `map_monsters` | `map_monsters.sql` | 2374 spawns from `npc/re/mobs/` with weight (`amount`) |
| `monster_drops` | `monster_drops.sql` | 12544 drops; `rate` at 0–100 scale (rAthena ÷ 100) |
| `players` | PlayerSeedLoader (startup) | Initial player |
| `player_items` | Generated in combat | Inventory (UUID PK, `is_equipped`, `amount`) |
| `skills` | `skills.sql` + `forceLoad` | Catalog: `aegis_name`, `name`, `effect_type`, `damage_formula`, `sp_cost`, `duration_turns` |
| `skill_tree` | `skill_tree.sql` | Tree by class: `job_class`, `skill_id`, `max_level`, `prereq_skill`, `prereq_level` |
| `skill_buff_effects` | `skill_effects.sql` | Buff/passive effects per skill: `stat_type`, `value_formula` |
| `player_skills` | JPA (test) / Flyway (prod) | Learned skills: `player_id`, `skill_id`, `current_level` |
| `weapon_size_modifiers` | `weapon_size_modifiers.sql` | Damage modifiers: `weapon_type`, `small_pct`, `medium_pct`, `large_pct` |

### Resetting database data

```sql
-- Re-import items (forces RathenaImporter on next startup):
DELETE FROM items;

-- Skills are always updated via forceLoad — no manual DELETE needed.

-- Full reset (respect FK order):
DELETE FROM monster_drops;
DELETE FROM map_monsters;
DELETE FROM monsters;
DELETE FROM items;
```

> On the next startup, `RathenaImporter` detects `itemRepo.count() == 0` and re-imports automatically. `StartupDataLoader` reloads all static SQLs.

### Auto startup

**No manual scripts required.** `StartupDataLoader` (`@Order(3)`) populates all static tables on boot if they are empty. The console shows progress:

```
Populating 'map_monsters' from db/map_monsters.sql...
Table 'map_monsters' populated with 2374 rows.
```

> SQLs are in `src/main/resources/db/`. Python scripts in `scriptsPython/` are used **only to regenerate** the SQLs when rAthena data changes.

---

## Python Scripts (`scriptsPython/`)

ETL pipeline that extracts data directly from rAthena repositories via GitHub and generates the SQLs for the database.

| Script | Function |
|---|---|
| `Migrate.py` | Runs all SQLs in the correct order |
| `map_parser.py` | Generates `maps.sql` from `db/map_index.txt` |
| `warp_parser.py` | Generates `map_portals_v2.sql` from `npc/re/warps/` |
| `mob_parser.py` | Generates `map_monsters.sql` from `npc/re/mobs/` |
| `drop_parser.py` | Generates `monster_drops.sql` from `db/re/mob_db.yml`; normalizes rate: `rAthena_rate / 100.0` |
| `skill_parser.py` | Generates `skill_tree.sql` from `db/re/skill_tree.txt` |

**Python dependencies:**
```bash
pip install requests pyyaml psycopg2-binary --break-system-packages
```

---

## Antifraude Integration

`ragnarok-core` integrates with `ragnarok-antifraude` (port 8081) to detect botting and cheating during gameplay.

### Request flow

```
[Client] → POST /api/battle/attack / /map/walk
  └─ BattleService / MapService
       └─ FraudClient.analyze(FraudRequest)
            └─ POST http://localhost:8081/api/fraud/analyze
                 Header: X-API-Key: dev-key-123
                 └─ FraudResponse { verdict, requiredAction, riskLevel }
```

### Fault tolerance

`FraudClient` is wrapped with a Resilience4j **CircuitBreaker**. If antifraude is unreachable or slow:
- Circuit opens after 5 failures
- Fallback returns `verdict=APPROVED, requiredAction=NONE` — game never blocks
- Antifraude can be started/stopped independently; core continues normally

### Shared types

| Enum | Values |
|---|---|
| `Verdict` | `APPROVED`, `BLOCKED`, `CHALLENGE`, `UNKNOWN` |
| `RequiredAction` | `NONE`, `CANCEL_ACTION`, `SHOW_CAPTCHA`, `DROP_SESSION`, `FLAG_FOR_REVIEW`, `ALERT_ONLY` |
| `RiskLevel` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |

### Starting antifraude

```bash
cd ragnarok-simulator/ragnarok-antifraude
docker compose up -d   # PostgreSQL + Redis
DB_PASS=postgre ANTIFRAUDE_API_KEY=dev-key-123 ./mvnw spring-boot:run
```

The `fraud` field appears in battle/walk responses when antifraude is active.

---

## Folder Structure

```text
com.ragnarok
├── api
│   ├── GlobalExceptionHandler.java       # @RestControllerAdvice — maps exceptions to HTTP codes
│   ├── controller
│   │   ├── BattleController.java         # POST /api/battle/attack
│   │   ├── ItemController.java           # GET/POST /api/players/{id}/inventory
│   │   ├── MapController.java            # GET/POST /api/players/{id}/map
│   │   ├── PlayerController.java         # GET/POST /api/players
│   │   └── SkillController.java          # GET/POST /api/players/{id}/skills
│   └── dto
│       ├── request/                      # AttackRequestDTO, CreatePlayerRequestDTO, TravelRequestDTO, UseSkillRequestDTO
│       └── response/                     # PlayerResponseDTO, BattleResponseDTO, SkillRowResponseDTO,
│                                         # InventoryItemResponseDTO, MapInfoResponseDTO, WalkResponseDTO
│
├── application
│   ├── dto
│   │   ├── SkillRowDTO.java              # Public record for skill display in the terminal
│   │   └── WalkResult.java              # record: encounterOccurred, monsterId, message
│   └── service
│       ├── BattleEventHandler.java       # @EventListener: loot persistence, XP, resurrection
│       ├── BattleService.java            # Combat turn; publishes MonsterKilledEvent / PlayerDiedEvent
│       ├── ClassChangeService.java       # Class progression
│       ├── ItemService.java              # Inventory, equip, auto-swap
│       ├── MapService.java               # Current map, portals, walk, travel
│       ├── MonsterCatalogService.java    # Monster ETL via external API
│       ├── PlayerService.java            # Character creation and management
│       ├── ScriptInterpreter.java        # Formula engine: "ATK * skill_lv * 1.3"
│       ├── SkillCombatService.java       # Skill usage in combat (HEAL, BUFF, DAMAGE)
│       ├── SkillService.java             # Listing (class chain), learning, passive application
│       └── WeaponSizeService.java        # Damage modifier: weapon_type × monster_size (@Cacheable)
│
├── domain
│   ├── event
│   │   ├── MonsterKilledEvent.java       # record(playerId, monsterId, List<Item> loot, baseExp, jobExp)
│   │   ├── PlayerDiedEvent.java          # record(playerId)
│   │   └── PlayerLeveledUpEvent.java     # record(playerId, newBaseLevel, newJobLevel)
│   ├── model
│   │   ├── ActiveBuff.java              # Active buff (skill, stat, value, remaining turns)
│   │   ├── BattleResult.java
│   │   ├── BuffFlag.java
│   │   ├── EffectResult.java            # Legacy script evaluation result
│   │   ├── ElementalDamage.java
│   │   ├── EquipSlot.java
│   │   ├── Item.java
│   │   ├── ItemDropInfo.java
│   │   ├── ItemStats.java               # Equipment stats Value Object
│   │   ├── ItemType.java
│   │   ├── JobClass.java                # Full enum with tier, parentClass, and base stats
│   │   ├── MainAttributes.java
│   │   ├── MainStats.java
│   │   ├── Monster.java
│   │   ├── MonsterDrop.java
│   │   ├── Player.java
│   │   ├── PlayerItem.java
│   │   ├── PlayerLocation.java
│   │   ├── PlayerStats.java
│   │   ├── SkillEffectType.java         # Enum: PHYSICAL_DAMAGE, MAGICAL_DAMAGE, BUFF, HEAL, PASSIVE
│   │   ├── SkillElement.java            # Enum: NEUTRAL, FIRE, WATER, WIND, EARTH, HOLY, SHADOW...
│   │   ├── StatType.java                # Enum: STR, AGI, VIT, INT, DEX, LUK, DEF, M_DEF, FLEE...
│   │   └── WeaponType.java              # Enum: SWORD, DAGGER, SPEAR, BOW, STAFF, MACE, NONE...
│   └── service
│       ├── BattleEngine.java            # Damage, loot RNG, modifiers — zero database dependency
│       └── LevelingService.java         # XP curve, Level Up, rewards
│
└── infrastructure
    ├── client
    │   ├── RagnapiClient.java
    │   ├── dto
    │   │   ├── ItemDTO.java
    │   │   └── MonsterDTO.java           # Immutable record, String fields to tolerate dirty data
    │   └── mapper
    │       ├── ItemMapper.java
    │       ├── MonsterMapper.java        # Sanitization + drop rate without division (DB already normalized)
    │       └── PlayerMapper.java         # Safe Unboxing (NULL → 0)
    │
    └── persistence
        ├── GameMapEntity.java / Repository
        ├── ItemEntity.java / Repository
        ├── MapMonsterEntity.java / Repository     # Spawns with amount (weighted draw)
        ├── MapPortalEntity.java / Repository
        ├── MonsterDropEntity.java
        ├── MonsterEntity.java / Repository
        ├── PlayerEntity.java / Repository
        ├── PlayerItemEntity.java / Repository     # UUID PK, is_equipped, amount
        ├── PlayerSkillEntity.java / Repository
        ├── SkillBuffEffectEntity.java / Repository  # stat_type + value_formula per skill
        ├── SkillEntity.java / Repository
        ├── SkillTreeEntity.java / Repository      # findByJobClassesIn (class chain)
        ├── WeaponSizeModifierEntity.java / Repository
        └── mapper
            ├── BuffSerializer.java                # JSON ↔ List<ActiveBuff>
            └── PlayerMapper.java
```

---

## Key Classes

### Domain Models

| Class | Description |
|---|---|
| `Monster` | Rich model with `MainStats`, `ElementalDamage`, list of `MonsterDrop` |
| `Player` | Rich model with inventory, `statPoints`, `skillPoints`, `xp`, `mapName`, `activeBuffs` |
| `Item` | Pure model with `ItemStats` (Value Object) and `WeaponType` |
| `MonsterDrop` | Associates `Item` with `rate` (Double, 0–100 scale) |
| `ActiveBuff` | Active buff: source skill, `StatType`, value, and `durationTurns` (-1 = permanent) |
| `JobClass` | Enum with all jobs (Tier 0–4), `parentClass` for progression chain, and base stats per class |

### Domain Events

| Event | Description |
|---|---|
| `MonsterKilledEvent` | Published by `BattleService` when monster HP reaches 0; carries loot list, baseExp, jobExp |
| `PlayerDiedEvent` | Published by `BattleService` when player HP reaches 0; triggers resurrection in `BattleEventHandler` |
| `PlayerLeveledUpEvent` | Published by `BattleEventHandler` when base or job level increases |

### Domain Services

| Class | Description |
|---|---|
| `BattleEngine` | Damage formula `(STR*2 + WeaponATK) - DEF`, loot RNG `nextDouble(0,100) < rate`, elemental modifier, size modifier |
| `LevelingService` | Curve `Level * 100`, Base and Job Level Up, Full Heal, stat/skill points distribution, caps per `JobClass` |

### Infrastructure Entities

| Entity | Details |
|---|---|
| `PlayerEntity` | Flattening: `base_exp`, `job_exp`, `stat_points`, `skill_points`, `map_name`, `active_buffs_json` |
| `MonsterDropEntity` | `rate` as `DOUBLE PRECISION` at 0–100 scale; FK to `monsters` and `items` |
| `SkillTreeEntity` | Read-only; multiple rows per skill = multiple prerequisites |
| `SkillBuffEffectEntity` | `skill_id` + `stat_type` + `value_formula` — evaluated at runtime by `ScriptInterpreter` |
| `WeaponSizeModifierEntity` | `weapon_type` + `small_pct` + `medium_pct` + `large_pct` |
| `PlayerItemEntity` | UUID PK, allows multiple instances of the same item (e.g., two katanas with different refine levels) |
| `MapMonsterEntity` | `map_id` + `monster_id` + `amount` — weighted draw by `amount` |

---

## Test Coverage

**286 tests — 0 failures — BUILD SUCCESS**

> Run with Java 17: `JAVA_HOME=/path/to/jdk-17 ./mvnw test`
> (Java 21+ breaks Mockito inline-mock-maker without additional `--add-opens` configuration)
>
> **Integration tests require Docker Desktop running** — Testcontainers starts a PostgreSQL container automatically.
>
> **Nota arquitetural:** `AbstractIntegrationTest` ainda usa `@MockBean` (deprecated no Spring Boot 3.4, marcado para remoção). Quando a versão for atualizada para 3.5+, migrar para `@MockitoBean`.

### Unit Tests

| Class | Tests | Coverage |
|---|---|---|
| `BattleEngineTest` | 20 | Damage formula, minimum damage 1, DEF=0, 100%/0%/50% loot, counter-attack, rate scale, null guards |
| `LevelingServiceTest` | 7 | XP curve, Level Up, excess XP reset, Full Heal, stat/skill points |
| `PlayerTest` | 38 | Inventory, getTotalDef, getWeaponAtk, activeBuffs, equipment |
| `ActiveBuffTest` | 4 | Construction, expiration, permanent (`durationTurns = -1`) |
| `MonsterMapperTest` | 9 | Rate 100/70/0.35/0.0/null, non-null item, invariant ≤100, empty/null drops |
| `ScriptInterpreterTest` | 16 | Arithmetic formulas, stat variables, edge cases |
| `WeaponSizeServiceTest` | 6 | Modifiers by weapon type and monster size |
| `ClassChangeServiceTest` | 14 | `listarClassesDisponiveis`, `trocarClasse` with repository mocks |
| `BattleServiceTest` | 10 | Normal attack, death (VICTORY), counter-attack, player death (FATAL), MonsterKilledEvent com loot e exp; retorna `AttackResult(message, monsterHpRemaining)` |
| `BattleServiceLoggingTest` | 2 | WARN on dead-player attack, INFO on monster kill — Logback `ListAppender` |
| `BattleEventHandlerTest` | 4 | Loot persistence, XP fields, level-up event publication, player resurrection |
| `SkillCombatServiceTest` | 9 | Skill not found, not learned, insufficient SP, passive, HEAL, BUFF with duration, BUFF without effects, PHYSICAL_DAMAGE with/without target |
| `AccountServiceTest` | 6 | Register, duplicate username 409, login (retorna `LoginResult`), senha errada lança `GameException`, `validateOwnership` correto e incorreto |
| `PlayerServiceTest` | 3 | Criar personagem + validação de flattening no banco, ressuscitar restaura HP, ressuscitar player inexistente lança exceção |
| `MarketServiceTest` | 6 | Listar, criar, comprar, cancelar listing — incluindo validações de owner e estoque |
| `CashShopServiceTest` | 4 | Listar itens, comprar com saldo suficiente/insuficiente, item inexistente |
| `NpcShopServiceTest` | 4 | Comprar/vender via NPC, item inexistente, player não encontrado |
| `AccountControllerTest` | 4 | REST: register 201, duplicate 409, login 200 com token, credenciais erradas 404 |
| `PlayerControllerTest` | 4 | REST: list, get by id, create player, not found 404 |
| `BattleControllerTest` | 3 | REST: attack, dead player 400, player/monster not found 404 |
| `SkillControllerTest` | 3 | REST: list skills, learn, use |
| `ItemControllerTest` | 2 | REST: list inventory, use item |
| `MapControllerTest` | 5 | REST: current map, portals, walk, travel, not found |
| `MarketControllerTest` | 2 | REST: list listings, buy |
| `TradeControllerTest` | 3 | REST: criar oferta, aceitar, rejeitar |
| `NpcShopControllerTest` | 2 | REST: listar itens NPC, comprar |
| `CashShopControllerTest` | 2 | REST: listar itens cash, comprar |
| `GlobalExceptionHandlerTest` | 5 | HTTP 400 / 404 / 500 mapping for all domain exceptions |
| `JwtUtilTest` | 3 | Geração de token, extração de accountId, token expirado |
| `CacheVerificationTest` | 2 | `@SpyBean` verifies `findByWeaponType` called exactly once in two consecutive hits |
| `ClassChangeLoggingTest` | 2 | Log messages for class change |
| `RagnarokTerminalRunnerTest` | 11 | Ressurreição pós-FATAL, moverParaMapa, handlePlayerDeath, caminhar (encontro/sem encontro), renderCharacterSelect (vazio/opção 0/seleção válida/múltiplos), renderExplorationMenu opção 6 |
| `ParserLoggingTest` | 4 | Log output from rAthena YAML parsers |

### Integration Tests (Testcontainers — require Docker)

| Class | Tests | Coverage |
|---|---|---|
| `BattleIntegrationTest` | 2 | Dano físico (StatusATK + WeaponATK - DEF) e morte do jogador por contra-ataque no banco real |
| `BattleLootIntegrationTest` | 1 | Drop RNG, inventory persistence, victory message includes drop names |
| `SkillServiceIntegrationTest` | 10 | Listing, available skills, HEAL, BUFF, insufficient SP, PASSIVE, PHYSICAL_DAMAGE, BUFF without effects |
| `SkillServiceAprenderTest` | 7 | Level increment, skillPoints decrement, max level, non-existent skill, null jobClass |
| `ClassChangeIntegrationTest` | 2 | `trocarClasse` persists `jobClass/jobLevel/jobExp`; listing for NOVICE |
| `ItemServiceIntegrationTest` | 15 | Inventory CRUD, equip/unequip, auto slot-swap |
| `StartupDataLoaderSqlTest` | 9 | `monster_drops.sql` and `map_monsters.sql`: FK safety, idempotency, WHERE EXISTS |
| `PlayerInventoryIntegrationTest` | 2 | Equip and unequip on real database |
| `MapSpawnIntegrationTest` | 1 | MapMonster persistence and retrieval by map ID |
| `MonsterDropIntegrationTest` | 1 | Drop reading from database |
| `MonsterCatalogServiceTest` | 1 | Full ETL API → database; cleanup corrigido para respeitar FK em `map_monsters` |
| `CacheVerificationTest` | 2 | `@SpringBootTest` + Testcontainers; verifica que `findByWeaponType` é chamado uma única vez em dois hits consecutivos |
| `RagnarokCoreApplicationTests` | 1 | Spring context loads successfully |

---

## Roadmap

### Completed

- **Class Chain for Skills:** LORD_KNIGHT sees and learns Knight and Swordsman skills
- **Skill Effects:** Buffs with `stat_type` + `value_formula`, permanent passives, formula-based damage
- **Size Modifiers:** `WeaponSizeService` + `weapon_size_modifiers` table
- **Formula Engine:** `ScriptInterpreter` evaluates mathematical expressions with stat variables at runtime
- **Effect Messages:** Using a skill shows concrete effects: `[DEF +2, M_DEF +4]`
- **Fix Drop Rate:** Scale normalized to 0–100 during SQL ingestion (`rAthena ÷ 100`)
- **Stat Distribution Menu:** Terminal allows spending `statPoints` on 6 attributes
- **Complete Skills System:** `skill_tree`/`player_skills`, listing, prerequisites, learning
- **Class Change:** `ClassChangeService`, progression NOVICE→Tier1→Tier2, menu via `C`
- **Auto Startup:** `StartupDataLoader` — zero manual scripts
- **Screen Clear:** `clearScreen()` with ANSI codes between menus
- **Inventory Stacks Correctly:** Dropping an existing item increments `amount`
- **Stat/skill points accumulate in multi-level-up**
- **LevelingService:** Base/job level caps per class via `JobClass.maxJobLevel()`
- **Schema Migration:** Flyway V1 migration, `ddl-auto=validate`
- **Resilience4j:** `@Retry` (3 attempts, 2 s exponential backoff) + `@CircuitBreaker` on `RathenaDownloadService`; fallback logs WARN and skips import
- **Testcontainers:** `AbstractIntegrationTest` (Singleton Container Pattern) — all integration tests use an ephemeral `postgres:16` container; no local DB required for `./mvnw test`
- **Spring Cache (Caffeine):** `@EnableCaching` + `CacheConfig` with 4 named caches; `@Cacheable` / `@CacheEvict` on `WeaponSizeService`, `SkillService`, `SkillTreeRepository`, `SkillBuffEffectRepository`
- **REST API + Swagger UI:** 6 controllers (Auth, Players, Battle, Skills, Inventory, Map), `GlobalExceptionHandler`, springdoc-openapi — full game loop playable at `http://localhost:8080/swagger-ui.html`
- **Spring Events:** `BattleService` + `SkillCombatService` decoupled via `MonsterKilledEvent` / `PlayerDiedEvent`; `BattleEventHandler` owns loot persistence, XP processing, and player resurrection
- **JWT Authentication:** `JwtFilter` at `@Order(1)` for all `/api/*` routes; `AccountService` issues and validates tokens; login returns `{token, accountId}`
- **CORS:** `FilterRegistrationBean<CorsFilter>` at `HIGHEST_PRECEDENCE` — OPTIONS preflight passes before JWT check; allows `http://localhost:3000`
- **Account ownership:** `accountId` saved on player creation, `GET /api/players` filters by JWT owner, `validateOwnership` guards all player endpoints
- **4 missing endpoints implemented:** `PUT /api/players/{id}/stats` (stat distribution), `POST /{id}/resurrect`, `GET/POST /{id}/class-change`, `POST /inventory/{itemId}/equip`
- **SkillCombatService death detection:** skill kill fires `MonsterKilledEvent` with loot+exp; `VITÓRIA` message returned to caller
- **RathenaImporter local classpath:** reads from `src/main/resources/rathena/` instead of GitHub; threshold checks prevent re-import of already-populated data
- **PlayerSeedLoader duplicate guard:** fixed from `existsById(1L)` to `existsByName("Hero")` — restarts no longer create duplicate seed players
- **Antifraude integration:** `FraudClient` calls antifraude microservice; Resilience4j CircuitBreaker with fail-open fallback
- **Suíte de testes green (286 testes):** corrigidos 7 erros de compilação (`BattleService.AttackResult` record em vez de `String` nos mocks), `PlayerControllerTest` completado com `@MockitoBean ClassChangeService`, `AccountServiceTest` corrigido para `GameException` nas credenciais inválidas, `PlayerServiceTest` com `@Transactional` + cleanup de dados de teste, `MonsterCatalogServiceTest` com DELETE em `map_monsters` antes de `monsters`, `RagnarokTerminalRunner` corrigido para usar `.message()` no retorno de `realizarAtaque`

### Backlog

#### High Priority

1. **`BattleResponseDTO` — multi-round fields** — add `monsterAlive` + `monsterHpRemaining` to the battle response so the front-end can loop attacks until the monster dies
2. **Use items in battle** — add "Item" option to combat menu for consumables in inventory
3. **Missing stat mechanics:**
   - **AGI** → FLEE (evasion) and ASPD (attack speed)
   - **DEX** → HIT (accuracy) and cast time reduction
   - **LUK** → critical rate and drop rate bonus
4. **Butterfly Wing / Fly Wing** — teleport to Prontera / random map point

#### Medium Priority

5. **Map encyclopedia** — neighboring maps, map monsters, drops with rarity
6. **NPC shop system** — buy/sell with Zenny in cities
7. **Class change restricted to NPCs** — allow only at specific locations

#### Future

- **Position persistence (X,Y)** — save coordinates on exit
- **Edge connections between maps** — geographically connected fields without NPC warp
- **Multiplayer (WebSockets)** — long term

---

## Build & Run

```bash
# Build
./mvnw clean install

# Run the application (interactive terminal — requires direct JVM stdin)
java -jar target/ragnarok-core-0.0.1-SNAPSHOT.jar

# Run all tests — requires Docker Desktop running (Testcontainers)
JAVA_HOME=/path/to/jdk-17 ./mvnw test

# Run only unit tests (no Docker needed)
JAVA_HOME=/path/to/jdk-17 ./mvnw test -Djacoco.skip=true \
  -Dtest="BattleServiceTest,BattleEventHandlerTest,BattleServiceLoggingTest,\
RagnarokTerminalRunnerTest,GlobalExceptionHandlerTest,\
AccountControllerTest,PlayerControllerTest,BattleControllerTest,\
SkillControllerTest,ItemControllerTest,MapControllerTest,\
MarketControllerTest,TradeControllerTest,NpcShopControllerTest,CashShopControllerTest,\
AccountServiceTest,PlayerServiceTest,MarketServiceTest,CashShopServiceTest,NpcShopServiceTest,\
WeaponSizeServiceTest,SkillCombatServiceTest,ItemServiceTest,ClassChangeServiceTest,\
JwtUtilTest,ClassChangeLoggingTest,ParserLoggingTest"

# Run a specific test
./mvnw test -Dtest=BattleEngineTest
```

**Prerequisites:**
- Java 17+
- PostgreSQL running on `localhost:5432` with database `ragnarok_db` (for the application)
- **Docker Desktop** (for `./mvnw test` — Testcontainers starts a `postgres:16` container automatically)
- Environment variable `DB_PASS` with the PostgreSQL password (local default: `postgre`)

> On the first startup, `RathenaImporter` downloads data from rAthena via GitHub (~2675 monsters + all items). `StartupDataLoader` populates the rest. The application is ready in ~30–60 seconds depending on connection speed.

### REST API / Swagger UI

Once the application is running, open **`http://localhost:8080/swagger-ui.html`** to play via browser without cloning the repo.

Recommended flow:
```
1. POST /api/players          — {"name":"Hero","jobClass":"NOVICE"}  → note the returned "id"
2. GET  /api/players/{id}     — verify HP, level, zenny
3. POST /api/players/{id}/map/walk  → if encounterOccurred=true, note monsterId
4. POST /api/battle/attack    — {"playerId":1,"monsterId":1002}  → repeat until VITÓRIA
5. GET  /api/players/{id}/inventory — verify dropped items
6. GET  /api/players/{id}/skills    — list learnable skills
7. POST /api/players/{id}/skills/NV_BASIC/learn
8. POST /api/players/{id}/map/travel — {"destination":"izlude"}
```
