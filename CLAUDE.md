# Ragnarok Core - CLAUDE.md

## Project Overview

Ragnarok Online emulator/simulator built with **Hexagonal Architecture (Ports and Adapters)**. Java 17, Spring Boot 3.4.2, PostgreSQL. All game data is sourced from the official **rAthena** server emulator repository (`db/re/` branch — Renewal).

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.4.2
- **Database:** PostgreSQL (`ragnarok_db` on localhost:5432)
- **Build:** Maven (`./mvnw`)
- **Dependencies:** Spring Data JPA, Spring Web, OpenFeign, Lombok, Jackson, SnakeYAML
- **Python Scripts:** Data ETL pipeline in `scriptsPython/` (requires `requests`, `pyyaml`, `psycopg2-binary`)

## Architecture (Hexagonal)

```
com.ragnarok
├── domain/          # Pure business logic — NO Spring/JPA annotations
│   ├── model/       # Rich domain models (Monster, Player, Item, etc.)
│   └── service/     # Domain services (BattleEngine, LevelingService)
├── application/     # Use cases / orchestration layer
│   └── service/     # BattleService, PlayerService, MonsterCatalogService, ItemService
├── infrastructure/  # External adapters
│   ├── client/      # OpenFeign clients + DTOs + Mappers (API -> Domain)
│   └── persistence/ # JPA Entities + Repositories + Mappers (Domain -> DB)
└── runner/          # App entry points, terminal UI, data importers
    └── importer/    # RathenaImporter, MobDbParser, ItemDbParser
```

**Golden Rule:** The `domain` package must never depend on Spring, JPA, or any infrastructure framework.

## Database

- **URL:** `jdbc:postgresql://localhost:5432/ragnarok_db`
- **User:** `postgres` / **Password:** `postgre`
- **ddl-auto:** `update`

### Tables

| Tabela | Origem dos dados | Descrição |
|---|---|---|
| `monsters` | RathenaImporter (startup) | 2675 monstros do `db/re/mob_db.yml` |
| `items` | RathenaImporter (startup) | Itens do `db/re/item_db_*.yml` |
| `maps` | `maps.sql` | Todos os mapas do `db/map_index.txt` |
| `map_portals` | `map_portals_v2.sql` | 1864 warps de `npc/re/warps/` |
| `map_monsters` | `map_monsters.sql` | 2374 spawns de `npc/re/mobs/` com amount |
| `monster_drops` | `monster_drops.sql` | 12544 drops de `db/re/mob_db.yml` com rate 1-10000 |
| `players` | MockMapLoader (startup) | Jogador ID=1 "Hero" |
| `player_items` | Gerado em combate | Inventário do jogador |
| `monster_spawns` | MockMapLoader (startup) | Legado — substituído por `map_monsters` |

### Rodando migrações SQL

```bash
cd scriptsPython
python3 Migrate.py
```

Ordem das migrações no `Migrate.py`:
1. `maps.sql`
2. `map_portals_v2.sql` ← usar sempre o v2, nunca o `map_portals.sql`
3. `map_monsters.sql`
4. `monster_drops.sql`

Se precisar resetar o banco, deletar na ordem correta (respeitar FKs):
```sql
DELETE FROM monster_drops;
DELETE FROM map_monsters;
DELETE FROM monster_spawns;
DELETE FROM monsters;
DELETE FROM items;
```

## Key Architectural Decisions

- **Flattening strategy:** Complex domain objects stored as flat columns — no nested `@Embeddable`.
- **Safe Unboxing:** `PlayerMapper` converts DB `NULL` numerics to `0` before domain.
- **Resilient DTOs:** `MonsterDTO` uses `String` fields; parsing happens in mappers.
- **UUID inventory:** `PlayerItemEntity` uses UUID primary key.
- **Auto-Swap equipment:** `ItemService` auto-unequips same `EquipSlot` on equip.
- **Re-only data:** ALL rAthena data uses `db/re/` — never mix with `pre-re/`.

## Navigation System (World Map)

The player navigates the world using real rAthena warp data.

**Key classes:**
- `MapPortalEntity` / `MapPortalRepository` — maps `map_portals` table
  - `findDestinosByMapFrom(String mapFrom)` — returns distinct destinations
  - `findFirstByMapFromAndMapTo(String from, String to)` — portal coordinates
- `MapMonsterEntity` / `MapMonsterRepository` — maps `map_monsters` table
  - `findByMapId(String mapId)` — returns monsters with JOIN FETCH

**Player location:** Stored in `PlayerEntity.mapName` (e.g., `"prontera"`, `"prt_fild08"`). Updated on every map change and on death (resets to `"prontera"`).

**Weighted random encounter:** Monster is selected by weighted RNG using `MapMonsterEntity.amount` as weight. Higher amount = higher spawn probability.

```java
// Example: prt_fild08 has Poring x87, Lunatic x67, Fabre x77
// Poring appears ~37% of the time proportionally
int totalPeso = entradas.stream().mapToInt(e -> e.getAmount()).sum();
int sorteio = rng.nextInt(totalPeso);
```

## Terminal UI (RagnarokTerminalRunner)

Main game loop with two states: exploration and battle.

**Exploration menu:**
1. Caçar monstros — weighted random encounter based on current map
2. Portais — lists real warp destinations from `map_portals`
3. Inventário
4. Ver Status
5. Sair

**Battle menu:**
- Shows player HP and monster HP each turn
- Attack calls `BattleService.realizarAtaque`
- On death: player revives at `prontera`

**Important:** `MonsterSpawnRepository` is no longer used in `RagnarokTerminalRunner`. Use `MapMonsterRepository` instead.

## Data Importers (Startup)

`RathenaImporter` runs at `@Order(1)` on startup:
- Downloads and parses `db/re/mob_db.yml` → saves to `monsters` (skips if count > 0)
- Downloads and parses `db/re/item_db_*.yml` → saves to `items` (skips if count > 0)

`MockMapLoader` runs at `@Order(2)` on startup:
- Creates player ID=1 "Hero" if not exists
- Creates map `prt_fild08` and spawns Poring/Pupa if not exists
- Sets `player.mapName = "prontera"` on creation

**SnakeYAML limit:** Both `MobDbParser` and `ItemDbParser` set `CodePointLimit` to 50MB to handle large rAthena YAML files:
```java
LoaderOptions options = new LoaderOptions();
options.setCodePointLimit(50 * 1024 * 1024);
```

## Python ETL Scripts (`scriptsPython/`)

| Script | Função |
|---|---|
| `Migrate.py` | Roda todos os SQLs no banco em ordem |
| `map_parser.py` | Gera `maps.sql` do `db/map_index.txt` |
| `warp_parser.py` | Gera `map_portals_v2.sql` de `npc/re/warps/` |
| `mob_parser.py` | Gera `map_monsters.sql` de `npc/re/mobs/` |
| `drop_parser.py` | Gera `monster_drops.sql` de `db/re/mob_db.yml` |

## Battle Mechanics

- **Damage formula:** `(STR*2 + WeaponATK) - EnemyDEF`
- **Monster counter-attack:** Calculated and displayed each turn
- **XP curve:** `Level * 100`
- **Level Up rewards:** +5 stat points, +1 skill point, full HP/SP heal
- **Loot:** RNG drop roll based on `monster_drops.rate` (1-10000, where 10000 = 100%)

## Build & Run

```bash
./mvnw clean install
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=BattleIntegrationTest
```

## Roadmap (Next Priorities)

1. **Stat distribution menu** — Terminal UI to spend `statPoints`
2. **Skills system** — `Skill`, `PlayerSkill` entities, `skillPoints` spending
3. **Elemental damage** — element + size modifiers in `BattleEngine`
4. **Map state persistence** — save player X,Y position
5. **NPC shop system** — buy/sell with Zenny
6. **World border connections** — fields connected by border (not NPC warps) are not yet in `map_portals`
