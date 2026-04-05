# CLAUDE.md — ragnarok-core

Spring Boot backend for the Ragnarok Online simulator. Hexagonal architecture (Domain / Application / Infrastructure / Runner).

**Stack:** Java 17, Spring Boot 3.4.2, PostgreSQL 16, Flyway, Resilience4j, Caffeine, springdoc-openapi
**Port:** 8080
**Active branch:** `feature/Alt-6.2`

---

## Authentication

All `/api/*` routes require `Authorization: Bearer <token>` **except** `/api/accounts/register` and `/api/accounts/login`.

- `JwtFilter` — `OncePerRequestFilter` at `@Order(1)`. Skips OPTIONS requests (CORS preflight). Sets `request.setAttribute("accountId", accountId)` for downstream controllers.
- `AccountService.login()` returns `LoginResult(String token, Long accountId)`. Credential errors throw `GameException` (→ HTTP 400), not `IllegalArgumentException` (→ 404).
- `AccountController` exposes `/api/accounts/register` and `/api/accounts/login`.

## CORS

`WebMvcConfig` registers a `FilterRegistrationBean<CorsFilter>` at `Ordered.HIGHEST_PRECEDENCE` (runs before `JwtFilter`).
Allowed origins: `http://localhost:3000`. All standard methods and headers are permitted.
Do NOT use `WebMvcConfigurer.addCorsMappings()` — it runs after servlet filters and causes 401 responses without CORS headers.

## Player Ownership

- `POST /api/players` — reads `accountId` from request attribute, passes to `PlayerService.criarNovoPersonagem()`, persisted on `PlayerEntity.accountId`.
- `GET /api/players` — calls `playerRepository.findByAccountId(accountId)`.
- All other player routes call `accountService.validateOwnership(accountId, playerId)` → throws `IllegalArgumentException` if mismatch → GlobalExceptionHandler maps to 404.
- `PlayerRepository` has `existsByName(String)` and `findByAccountId(Long)`.

## Endpoints

### Auth
| Method | Path | Notes |
|---|---|---|
| POST | `/api/accounts/register` | `{username, password, email}` → `{accountId}` |
| POST | `/api/accounts/login` | `{username, password}` → `{token, accountId}` |

### Players
| Method | Path | Notes |
|---|---|---|
| GET | `/api/players` | Filtered by JWT accountId |
| GET | `/api/players/{id}` | Validates ownership |
| POST | `/api/players` | `{name, jobClass}` — binds accountId from JWT |
| PUT | `/api/players/{id}/stats` | `{str,agi,vit,int,dex,luk}` — spends statPoints |
| POST | `/api/players/{id}/resurrect` | Restores HP to max, stays on current map |
| GET | `/api/players/{id}/class-change` | Lists available job classes |
| POST | `/api/players/{id}/class-change` | `{targetClass}` — promotes to new job class |

### Battle
| Method | Path |
|---|---|
| POST | `/api/battle/attack` — `{playerId, monsterId}` |

### Skills
| Method | Path |
|---|---|
| GET | `/api/players/{playerId}/skills` |
| POST | `/api/players/{playerId}/skills/{skillName}/learn` |
| POST | `/api/players/{playerId}/skills/{skillName}/use` |

> `skillName` = `aegisName`, not the display name.

### Inventory
| Method | Path |
|---|---|
| GET | `/api/players/{playerId}/inventory` |
| POST | `/api/players/{playerId}/inventory/{itemId}/use` |
| POST | `/api/players/{playerId}/inventory/{itemId}/equip` |

`InventoryItemResponseDTO` fields: `id` (UUID), `name`, `type`, `amount`, `equipped`, `imgUrl` (`/assets/items/{id}.png` or null), `description` (text from bRO lua or null).

`NpcResponseDTO` fields: `id`, `name`, `type`, `x`, `y`, `spriteRef`, `spriteUrl` (`/ro-assets/output-npcs/{jobId}_0_0.png` after populator runs, otherwise null).

### Map
| Method | Path |
|---|---|
| GET | `/api/players/{playerId}/map` |
| GET | `/api/maps/{mapId}/portals` |
| POST | `/api/players/{playerId}/map/walk` |
| POST | `/api/players/{playerId}/map/travel` |

> `travel` returns HTTP 200 with no body (no JSON Content-Type).

### Market, Trade, NPC Shop, Cash Shop
See root `CLAUDE.md` for full tables.

---

## Key Services

| Service | Notes |
|---|---|
| `AccountService` | Register, login (`LoginResult`), `validateOwnership` |
| `PlayerService` | Create (with `accountId`), list (filtered), stat distribution, resurrect |
| `BattleService` | Physical attack; fires `MonsterKilledEvent` / `PlayerDiedEvent` |
| `SkillCombatService` | Skill use; fires `MonsterKilledEvent` on kill (same as BattleService) |
| `ClassChangeService` | `listarClassesDisponiveis`, `trocarClasse` |
| `ItemService` | Inventory, use, `equiparItem(playerId, itemId)` (toggle equip/unequip) |
| `MapService` | Current map, portals, walk (encounter), travel |
| `BattleEventHandler` | `@EventListener`: loot persistence, XP, level up, resurrection |
| `NpcSpritePopulator` | Resolves NPC spriteRef → JT constant → jobId → sprite URL, persists to `npc.sprite_url` |

---

## Infrastructure

### JwtFilter decision table

| Condition | Behavior |
|---|---|
| OPTIONS request | Pass through (CORS preflight) |
| Path in PUBLIC_PATHS | Pass through, no `accountId` set |
| Valid Bearer token | Set `accountId` attribute, continue |
| Missing or invalid token | Return 401 |

### RathenaImporter

Reads from `ClassPathResource("rathena/mob_db.yml")` etc. — files in `src/main/resources/rathena/`.
Threshold: skips re-import if DB already has ≥2600 monsters or ≥25000 items.
`readClasspath()` throws `IllegalStateException` if the file is missing — fails loud on startup.
`RathenaDownloadService` is `@Deprecated` and no longer a Spring bean.

### bRO Client Data Populator — `com.ragnarok.runner.populator`

One-time migration tool that enriches items and NPCs already imported by `RathenaImporter` / `NpcSeedLoader` with client-side data from the bRO game client.

**Files:**
| Class | Responsibility |
|---|---|
| `ItemInfoLuaParser` | Parses `iteminfo.lua` (EUC-KR encoding, UTF-8 fallback). Returns `Map<Integer, ItemClientData>`. |
| `SpriteConverter` | Indexes PNGs from `icones-png/` by filename (lowercase), copies to `static/assets/items/{itemId}.png`. |
| `ItemInfoPopulator` | `@Component`. Orchestrates items: parse lua → copy sprites → update `ItemEntity` (name, imgUrl, description). |
| `NpcLuaParser` | Plain class (no `@Component`). Parses `npcidentity_decompiled.lua` (UTF-16 LE with BOM). Returns `Map<String, Integer>` of constant→jobId. |
| `NpcSpritePopulator` | `@Component`. Bridges seed `spriteRef` names → `JT_*` lua constants → jobId → `/ro-assets/output-npcs/{jobId}_0_0.png`. Updates `npc.sprite_url`. |
| `ClientDataRunner` | `CommandLineRunner` at `@Order(5)`. Only runs when `ro.assets.run-populator=true`. Calls `ItemInfoPopulator.run()` then `NpcSpritePopulator.run()`. |
| `dto/ItemClientData` | Immutable DTO: `itemId`, `displayName`, `resourceName`, `description`. |

**Configuration (`application.properties`):**
```properties
ro.assets.run-populator=false                    # set true to run on next startup, then revert
ro.assets.iteminfo-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/iteminfo.lua
ro.assets.icons-png-path=C:/Users/silve/Documents/Sprites-Projeto/icones-png/
ro.assets.icons-output-path=src/main/resources/static/assets/items/
ro.assets.npc-identity-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/npcidentity_decompiled.lua
ro.assets.external-path=C:/Users/silve/Documents/Sprites-Projeto   # base dir for /ro-assets/** handler
```

**Flow:**
1. `RathenaImporter` and `NpcSeedLoader` must have already run (tables populated)
2. Run zrenderer bat to generate `output-monsters/` and `output-npcs/` under `external-path`
3. Set `ro.assets.run-populator=true`, start app
4. Populator: items (parse lua → copy PNGs → update DB) + NPCs (parse npcidentity lua → resolve jobIds → update `sprite_url`)
5. Revert `ro.assets.run-populator=false`

**Output:**
- Item PNGs: `GET /assets/items/{itemId}.png` (Spring static resources from `src/main/resources/static/`)
- Monster sprites: `GET /ro-assets/output-monsters/{jobId}_0_0.png`
- NPC sprites: `GET /ro-assets/output-npcs/{jobId}_0_0.png`

**NPC spriteRef bridge (hardcoded in `NpcSpritePopulator`):**
| `spriteRef` (seed) | `JT_*` constant |
|---|---|
| `kafra` | `JT_4_F_KAFRA1` |
| `warp_portal` | `JT_WARPNPC` |
| `npc_generic` | `JT_1_F_01` |
| `shop_generic` | `JT_1_F_MERCHANT_01` |

**Caveats:**
- `icons-output-path` is a relative path — only works when launched via `./mvnw spring-boot:run` from project root
- `npcidentity_decompiled.lua` is UTF-16 LE with BOM (`FF FE`) — `NpcLuaParser` handles both with-BOM and without-BOM variants
- `ClientDataRunner` is `@Order(5)` — must run after `NpcSeedLoader` (`@Order(4)`)
- Idempotent — re-running overwrites with same data
- `NpcSpritePopulator` saves each NPC individually (no bulk save) — acceptable given the small NPC dataset

### RoAssetsConfig — External Asset Serving

`RoAssetsConfig` implements `WebMvcConfigurer` and maps `/ro-assets/**` to an external filesystem path.

```java
// ro.assets.external-path defaults to "" — handler skipped if blank
@Value("${ro.assets.external-path:}")
private String externalPath;

@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (externalPath.isBlank()) return;
    registry.addResourceHandler("/ro-assets/**")
            .addResourceLocations("file:///" + externalPath.replaceAll("/+$", "") + "/");
}
```

- Safe on machines without `ro.assets.external-path` set (e.g., CI, other devs) — handler simply won't register
- The `output-monsters/` and `output-npcs/` subdirs under `external-path` are where zrenderer writes rendered PNGs

### Flyway Migrations

| Version | File | Change |
|---|---|---|
| V1 | `V1__init.sql` | Initial schema |
| V2 | `V2__*.sql` | (existing) |
| V3 | `V3__npc_sprite_url.sql` | `ALTER TABLE npcs ADD COLUMN sprite_url VARCHAR(255)` |

### PlayerSeedLoader

Checks `existsByName("Hero")` before seeding — prevents duplicate "Hero" players on restart.

### GlobalExceptionHandler

| Exception | HTTP |
|---|---|
| `GameException` | 400 |
| `IllegalArgumentException` | 404 |
| `NoResourceFoundException` | 404 |
| Other `RuntimeException` | 500 |

> `NoResourceFoundException` must be handled explicitly — otherwise Spring's static-resource 404s fall into the `Exception.class` catch-all and return 500.

---

## Conventions

- `jobClass` in `PlayerResponseDTO` is a `String` mapping to the `JobClass` enum
- `intelligence` is the internal field name on `PlayerEntity`; the stats payload uses `"int"` as JSON key (`@JsonProperty("int")`)
- `zenny` (two n's)
- `statPoints` — not `statusPoints`
- `hpCurrent / hpMax` and `spCurrent / spMax`
- `LoginResponseDTO(String token, Long accountId)`
- `UpdateStatsRequestDTO` — all fields nullable; only values > 0 are applied

---

## Known Gaps / Backlog

- `BattleResponseDTO` does not expose `monsterAlive` / `monsterHpRemaining` — front can't loop attacks until death without polling
- NPC shop, Market, Trade, Cash Shop controllers exist but are not connected to antifraude fraud checks
- Skills in battle (REST) return the kill message from `SkillCombatService` but the front-end has no UI for it yet
- Monster sprites served via `/ro-assets/output-monsters/{jobId}_0_0.png` — requires zrenderer bat to have run first; front falls back to CDN (`ratemyserver.net`) then error icon
- NPC sprites served via `/ro-assets/output-npcs/{jobId}_0_0.png` — requires populator (`ro.assets.run-populator=true`) to have run; `spriteUrl` is null until then

---

## Running

```bash
# Requires PostgreSQL running (default: localhost:5432/ragnarok_db, user: postgres, pass: postgre)
./mvnw spring-boot:run

# Tests (requires Docker Desktop for Testcontainers)
./mvnw test
```

Startup order in the ecosystem: **antifraude (8081) → core (8080) → front (3000)**
