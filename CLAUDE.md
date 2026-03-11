# Ragnarok Core - CLAUDE.md

## Project Overview

Ragnarok Online emulator/simulator built with **Hexagonal Architecture (Ports and Adapters)**. Java 17, Spring Boot 3.4.2, PostgreSQL, Flyway migrations.

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.4.2
- **Database:** PostgreSQL (`ragnarok_db` on localhost:5432)
- **Migrations:** Flyway (`src/main/resources/db/migration`)
- **Build:** Maven (`./mvnw`)
- **Dependencies:** Spring Data JPA, Spring Web, OpenFeign, Lombok, Jackson, SnakeYAML

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

## Key Architectural Decisions

- **Flattening strategy:** Complex domain objects (e.g., `PlayerStats`, `PlayerLocation`) are stored as flat columns in DB entities — no nested `@Embeddable` unless already established.
- **Safe Unboxing:** `PlayerMapper` converts DB `NULL` numerics to `0` before passing to domain to prevent `NullPointerException`.
- **Resilient DTOs:** `MonsterDTO` uses `String` fields to tolerate malformed external API data; parsing/sanitization happens in mappers.
- **UUID inventory:** `PlayerItemEntity` uses UUID primary key allowing multiple instances of same item with different refinements.
- **Auto-Swap equipment:** When equipping an item, `ItemService` auto-unequips whatever occupies the same `EquipSlot`.

## Database

- **URL:** `jdbc:postgresql://localhost:5432/ragnarok_db`
- **User:** `postgres` / **Password:** `postgre`
- **Schema managed by Flyway** — DDL changes go in `src/main/resources/db/migration/`
- `spring.jpa.hibernate.ddl-auto=update` is set but Flyway handles migrations

## Build & Run Commands

```bash
# Build
./mvnw clean install

# Run application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run specific test
./mvnw test -Dtest=BattleIntegrationTest
```

## Domain Models Reference

| Class | Description |
|---|---|
| `Monster` | Enemy with `MainStats`, `drops`, `baseExp`, `jobExp` |
| `Player` | Character with `PlayerStats`, `PlayerLocation`, inventory, XP/Level |
| `Item` | Equipment/consumable with `ItemStats` value object |
| `MonsterDrop` | Association of `Item` + drop rate (`Double`) |
| `PlayerItem` | Inventory entry with UUID, refine level, `is_equipped` |
| `BattleResult` | Result of a combat turn |
| `ElementalDamage` | Elemental typing for damage calculation |

## Battle Mechanics

- **Damage formula:** `(STR*2 + WeaponATK) - EnemyDEF`
- **XP curve:** `Level * 100`
- **Level Up rewards:** +5 stat points, +1 skill point, full HP/SP heal
- **Loot:** RNG drop roll based on `MonsterDropEntity.rate`

## Testing

Integration tests use a real PostgreSQL connection. Tests cover:
- `MonsterCatalogServiceTest` — ETL flow: API -> sanitize -> persist -> spawn/drop mining
- `PlayerServiceTest` — Character factory, initial stats, flattening
- `BattleIntegrationTest` — Damage math
- `BattleLootIntegrationTest` — Drop table RNG + inventory save
- `LevelingIntegrationTest` — XP curve, reset, rewards
- `PlayerInventoryIntegrationTest` — Equip/Unequip, Auto-Swap

## Current Branch Strategy

- `main` — stable branch
- `Alt-01` — current active development branch

## Roadmap (Next Priorities)

1. Stat distribution menu (Terminal UI for spending `statPoints`)
2. Skills system (`Skill`, `PlayerSkill` entities, `skillPoints` spending)
3. Elemental damage refactoring (element + size modifiers in `BattleEngine`)
4. Map state persistence (save player X,Y position)
5. NPC shop system (buy/sell with Zenny)