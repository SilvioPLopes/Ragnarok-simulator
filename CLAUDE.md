# CLAUDE.md — ragnarok-core

Spring Boot backend for the Ragnarok Online simulator. Hexagonal architecture (Domain / Application / Infrastructure / Runner).

**Stack:** Java 17, Spring Boot 3.4.2, PostgreSQL 16, Flyway, Resilience4j, Caffeine, springdoc-openapi
**Port:** 8080
**Active branch:** `feature/Alt-6.2.2`

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

`BattleResponseDTO` fields: `message` (String), `monsterHpRemaining` (Integer). Front can loop attacks until `monsterHpRemaining <= 0`.

### Skills
| Method | Path |
|---|---|
| GET | `/api/players/{playerId}/skills` |
| POST | `/api/players/{playerId}/skills/{skillName}/learn` |
| POST | `/api/players/{playerId}/skills/{skillName}/use` |

> `skillName` = `aegisName`, not the display name.

`SkillRowResponseDTO` fields: `aegisName`, `name`, `maxLevel`, `currentLevel`, `canLearn`, `blockedReason`.

### Inventory
| Method | Path |
|---|---|
| GET | `/api/players/{playerId}/inventory` |
| POST | `/api/players/{playerId}/inventory/{itemId}/use` |
| POST | `/api/players/{playerId}/inventory/{itemId}/equip` |

`InventoryItemResponseDTO` fields: `id` (UUID), `name`, `type`, `amount`, `equipped`, `imgUrl` (`/assets/items/{id}.png` or null), `description` (text from bRO lua or null).

### Map
| Method | Path |
|---|---|
| GET | `/api/players/{playerId}/map` |
| GET | `/api/maps/{mapId}/portals` |
| POST | `/api/players/{playerId}/map/walk` |
| POST | `/api/players/{playerId}/map/travel` |

> `travel` returns HTTP 200 with no body (no JSON Content-Type).

`MapInfoResponseDTO` fields: `currentMap` (String), `displayName` (String — localized name from navi_map_br.lua, or null), `availablePortals` (List<String>).

### NPCs (NpcController — `/api`)
| Method | Path | Notes |
|---|---|---|
| GET | `/api/maps/{mapName}/npcs` | Returns NPCs for a map (no ownership check) |
| GET | `/api/npcs/{npcId}/shop` | Returns `NpcShopResponseDTO` |
| GET | `/api/npcs/{npcId}/dialog` | Returns `NpcDialogResponseDTO` — `{ "dialog": "texto..." }` or `null` |
| POST | `/api/npcs/{npcId}/buy` | `{playerId, itemId, amount}` — ownership validated |
| POST | `/api/npcs/{npcId}/heal` | `{playerId}` — ownership validated |
| POST | `/api/npcs/{npcId}/warp` | `{playerId, destination}` — ownership validated |

`NpcResponseDTO` fields: `id`, `name`, `type`, `x`, `y`, `spriteRef`, `spriteUrl` (`/ro-assets/output-npcs/{jobId}/0-0.png` after populator runs, otherwise null).
`NpcDialogResponseDTO` fields: `dialog` (String — concatenated `mes()` calls from rAthena script, or null if NPC has no dialog).

> **Note:** Legacy `NpcShopController` at `/api/shop/npc` (buy/sell/list) still exists but has NO ownership validation — known security gap.

### Market, Trade, Cash Shop
| Method | Path |
|---|---|
| GET/POST | `/api/market/**` |
| GET/POST | `/api/trade/**` |
| GET/POST | `/api/cashShop/**` |

---

## Key Services

| Service | Notes |
|---|---|
| `AccountService` | Register, login (`LoginResult`), `validateOwnership` |
| `PlayerService` | Create (with `accountId`), list (filtered), stat distribution, resurrect |
| `BattleService` | Physical attack; fires `MonsterKilledEvent` / `PlayerDiedEvent` |
| `SkillCombatService` | Skill use; fires `MonsterKilledEvent` on kill (same as BattleService). Dead-player guard added. |
| `ClassChangeService` | `listarClassesDisponiveis`, `trocarClasse` |
| `ItemService` | Inventory, use, `equiparItem(playerId, itemId)` (toggle equip/unequip) |
| `NpcService` | NPCs per map, NPC shop items, buy from NPC, heal, warp, `getDialog(npcId)` |
| `NpcShopService` | Legacy buy/sell via `/api/shop/npc` (no ownership check) |
| `MapService` | Current map (`MapInfoResponseDTO` with displayName), portals, walk (encounter), travel |
| `BattleEventHandler` | `@EventListener`: loot persistence, XP, level up, resurrection |
| `NpcSpritePopulator` | Resolves seed NPC spriteRef → JT constant → jobId → sprite URL (only for seed NPCs — kafra, warp_portal, etc.). Navi NPCs now get spriteUrl directly from `NaviNpcPopulator`. |

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

One-time migration tool that enriches items, skills, NPCs, maps, portals and mob spawns with client-side data from the bRO game client.

**Classes:**
| Class | Responsibility |
|---|---|
| `ItemInfoLuaParser` | Parses `iteminfo.lua` (EUC-KR encoding, UTF-8 fallback). Returns `Map<Integer, ItemClientData>`. Applies `convertLuaEscapes()` and `removeColorCodes()` on all String fields. |
| `SpriteConverter` | Indexes PNGs from `icones-png/` by filename (lowercase), copies to `static/assets/items/{itemId}.png`. |
| `ItemInfoPopulator` | `@Component`. Orchestrates items: parse lua → copy sprites → update `ItemEntity` (name, imgUrl, description). |
| `NpcLuaParser` | Plain class (no `@Component`). Parses `npcidentity_decompiled.lua` (UTF-16 LE with BOM). Returns `Map<String, Integer>` of constant→jobId. |
| `NpcSpritePopulator` | `@Component`. Bridges seed `spriteRef` names → `JT_*` lua constants → jobId → `/ro-assets/output-npcs/{jobId}_0_0.png`. Updates `npc.sprite_url`. Dynamic match: tries `JT_<SPRITEREF_UPPERCASE>` for all spriteRefs in DB, with 4-entry hardcoded fallback. |
| `NaviMapLuaParser` | Parses `navi_map_br.lua` → `Map<String, String>` (mapId → displayName). |
| `NaviMapPopulator` | `@Component`. Updates `maps.display_name` for all maps. |
| `NaviNpcLuaParser` | Parses `navi_npc_br.lua` → `List<NaviNpcData>` (mapName, npcId, name, spriteClass, **spriteJobId**, x, y). Field [3] = `spriteJobId` is the direct jobId for sprite URL. |
| `NaviNpcPopulator` | `@Component`. Creates or updates NPC records with coordinates, spriteRef, and **spriteUrl** (built directly from `spriteJobId` — no npcidentity.lua lookup needed). |
| `NaviLinkLuaParser` | Parses `navi_link_br.lua` → `List<NaviLinkData>` (portal warp data). |
| `NaviLinkPopulator` | `@Component`. Populates `map_portals` table with portal/warp data from client. |
| `NaviMobLuaParser` | Parses `navi_mob_br.lua` → `List<NaviMobData>` (monster spawn data per map). |
| `NaviMobPopulator` | `@Component`. Validates/enriches `map_monsters` table with client spawn data. |
| `NpcShopImporter` | `@Component`. Reads `rathena/shops.txt` (classpath) → populates `npc_shop_items`. Extracts `spriteId` (first token of col[3]) and sets `sprite_url` on each shop NPC. Always updates sprite URLs even when items are already populated (threshold only skips item insertion). |
| `RathenaNpcScriptParser` | Plain class (no `@Component`). Parses rAthena NPC `.txt` scripts from `cities/`, `kafras/`, `jobs/`, `other/`, `merchants/`. Extracts `(mapName, x, y, name, spriteId, dialog)` from `script` blocks. Ignores templates (`-` map), `duplicate()`, `shop`, `warp` lines. Removes color codes (`^RRGGBB`). |
| `RathenaDialogPopulator` | `@Component`. Calls `RathenaNpcScriptParser.parseDirectory()`, matches by `(mapName, x, y)`, saves `npcs.dialog`. Also sets `sprite_url` if null. Creates new NpcEntity (seedId = `rathena_{map}_{x}_{y}`) when no existing NPC matches. Skipped silently if `ro.assets.rathena-npc-path` is blank. |
| `SkillInfoListLuaParser` | Parses `skillinfolist.lua` → skill display names and metadata. |
| `SkillDescriptLuaParser` | Parses `skilldescript.lua` + `skillid.lua` → skill descriptions keyed by aegisName. |
| `SkillClientDataPopulator` | `@Component`. Orchestrates skill enrichment: update `skills.description` and `skills.img_url`. |
| `ClientDataRunner` | `CommandLineRunner` at `@Order(5)`. Only runs when `ro.assets.run-populator=true`. Calls all populators in order: items → NPC sprites → map names → NPC data → portals → NPC shops → skills → mob spawns → **rAthena dialogs**. |
| `dto/ItemClientData` | Immutable DTO: `itemId`, `displayName`, `resourceName`, `description`. |
| `dto/NaviNpcData` | DTO: `mapName`, `npcId`, `name`, `spriteClass`, `spriteJobId`, `x`, `y`. |
| `dto/NaviLinkData` | DTO for portal/warp navi data. |
| `dto/NaviMobData` | DTO for monster spawn navi data. |
| `dto/RathenaNpcScriptData` | DTO: `mapName`, `x`, `y`, `name`, `spriteId`, `dialog`. |

**Configuration (`application.properties`):**
```properties
ro.assets.run-populator=true                   # set true to run on next startup, then revert
ro.assets.iteminfo-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/iteminfo.lua
ro.assets.icons-png-path=C:/Users/silve/Documents/Sprites-Projeto/icones-png/
ro.assets.icons-output-path=src/main/resources/static/assets/items/
ro.assets.npc-identity-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/npcidentity_decompiled.lua
ro.assets.external-path=C:/Users/silve/Documents/Sprites-Projeto
ro.assets.skill-identity-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/skillid.lua
ro.assets.skillinfolist-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/skillinfolist.lua
ro.assets.skilldescript-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/skilldescript.lua
ro.assets.navi-map-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/navi_map_br.lua
ro.assets.navi-npc-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/navi_npc_br.lua
ro.assets.navi-link-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/navi_link_br.lua
ro.assets.navi-mob-lua-path=C:/Users/silve/Documents/Sprites-Projeto/item-info/navi_mob_br.lua
ro.assets.rathena-npc-path=C:/Users/silve/Documents/Sprites-Projeto/rathena-master/npc
```

**Full populator run order (inside `ClientDataRunner.run()`):**
1. `itemInfoPopulator.run()` — items: lua → PNGs → DB
2. `npcSpritePopulator.run()` — sprite URLs for seed NPCs (kafra, warp_portal, etc.) via npcidentity.lua
3. `naviMapPopulator.run()` — map display names
4. `naviNpcPopulator.run()` — NPC coordinates + spriteUrl direct from spriteJobId
5. `naviLinkPopulator.run()` — portals/warps
6. `npcShopImporter.run()` — NPC shop inventories + spriteUrl for shop NPCs
7. `skillClientDataPopulator.run()` — skill descriptions and icons
8. `naviMobPopulator.run()` — monster spawns
9. `rathenaDialogPopulator.run()` — NPC dialogs from rAthena scripts; sprite fallback for unmatched NPCs

**Pre-requisites before running populator:**
1. `RathenaImporter` and `NpcSeedLoader` must have already run (tables populated)
2. Run zrenderer bat to generate `output-monsters/` and `output-npcs/` under `external-path`
3. Set `ro.assets.run-populator=true`, start app
4. Revert `ro.assets.run-populator=false`

**Output:**
- Item PNGs: `GET /assets/items/{itemId}.png` (Spring static resources from `src/main/resources/static/`)
- Monster sprites: `GET /ro-assets/output-monsters/{jobId}_0_0.png`
- NPC sprites: `GET /ro-assets/output-npcs/{jobId}/0-0.png` (subdirectory format — files at `external-path/output-npcs/{jobId}/0-0.png`)
- Map minimaps: `GET /ro-assets/data/map/{mapName}.bmp` (already available from external-path, no code change needed)

**NPC spriteRef bridge (hardcoded fallback in `NpcSpritePopulator`):**
| `spriteRef` (seed) | `JT_*` constant |
|---|---|
| `kafra` | `JT_4_F_KAFRA1` |
| `warp_portal` | `JT_WARPNPC` |
| `npc_generic` | `JT_1_F_01` |
| `shop_generic` | `JT_1_F_MERCHANT_01` |

For all other spriteRefs in the DB, `NpcSpritePopulator` dynamically tries `JT_<SPRITEREF_UPPERCASE>` then `<SPRITEREF_UPPERCASE>` as lookup keys in the parsed `npcidentity_decompiled.lua`.

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
- `data/map/` subdir under `external-path` contains `.bmp` minimap files served at `/ro-assets/data/map/{mapName}.bmp`

### Flyway Migrations

| Version | File | Change |
|---|---|---|
| V1 | `V1__initial_schema.sql` | Initial schema (maps, monsters, items, players, map_portals, map_monsters, skills, ...) |
| V2 | `V2__npc_tables.sql` | `npcs`, `npc_shop_items`, `npc_warp_destinations` tables |
| V3 | `V3__npc_sprite_url.sql` | `ALTER TABLE npcs ADD COLUMN sprite_url VARCHAR(255)` |
| V4 | `V4__missing_portal_exits.sql` | INSERT portal exits for 7 soft-locked maps |
| V5 | `V5__skill_enrichment.sql` | `ADD COLUMN description TEXT` and `img_url VARCHAR(500)` to `skills` |
| V6 | `V6__map_display_name.sql` | `ADD COLUMN display_name VARCHAR(255)` to `maps` |
| V7 | `V7__npc_dialog.sql` | `ADD COLUMN dialog TEXT` to `npcs` |

> `maps.img_url` already existed since V1. `npc_shop_items` already existed since V2. Next migration must be **V8**.

### Key Entity Fields

**`NpcEntity`** (`npcs` table):
- `id`, `seedId` (unique), `name`, `type` (NpcType enum), `x`, `y`, `mapName`, `spriteRef`, `spriteUrl`, `dialog` (TEXT)
- `@OneToMany shopItems`, `@OneToMany warpDestinations`

**`GameMapEntity`** (`maps` table):
- `id` (String, e.g. `"moc_fild08"`), `name`, `type`, `imgUrl`, `displayName` (localized name from navi_map_br.lua)

**`SkillEntity`** (`skills` table):
- `id`, `aegisName`, `name`, `type`, `script`, `effectType`, `element`, `damageFormula`, `spCost`, `durationTurns`, `targetType`
- `description` (TEXT — populated by SkillClientDataPopulator)
- `imgUrl` (`img_url VARCHAR(500)` — populated by SkillClientDataPopulator)

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
- Lua files are from the Brazilian (bRO) client — suffix `_br` (e.g., `navi_npc_br.lua`)

---

## Known Bugs / Security Gaps (open)

1. **`ItemService.gerenciarEquipamento()` — ownership check always false** (line ~59)
   - `if (!itemAlvo.getPlayer().getId().equals(itemAlvo.getPlayer().getId()))` — compares ID to itself, always false. Any player can equip an item they don't own if they know the UUID.

2. **`NpcShopController` at `/api/shop/npc` — no ownership validation**
   - `buy()` and `sell()` accept any `playerId` without checking the JWT `accountId`. Contrast with `NpcController` at `/api/npcs/{npcId}/buy` which does validate.

3. **`MarketService.createListing()` — item not reserved when listed**
   - Listed item stays in inventory without a "reserved" flag. Player can use, sell elsewhere, or list the same item multiple times before a buyer completes the purchase.

4. **`MapService.walk()` — monster HP reset to 100 hardcoded**
   - Dead monsters (hp ≤ 0 in DB) are reset to `100` instead of `baseHp`. A Poring (55 HP) could reappear with 100 HP.

5. **`BattleService` — monster HP is shared global state**
   - No `@Lock(PESSIMISTIC_WRITE)` on monster repository. Two concurrent players attacking the same `monsterId` cause a race condition.

6. **`LevelingService.processarExperiencia()` — HP/SP restored using `stats.maxHp` instead of `entity.hpMax`**
   - If `hpMax` is stale (not recalculated with buffs/equips), level-up restores wrong value.

## Known Gaps / Backlog

- Skills DTO (`SkillRowResponseDTO`) does not expose `description` or `imgUrl` — the data exists in `SkillEntity` but is not surfaced to the front yet
- `SkillRowResponseDTO` fields: `aegisName`, `name`, `maxLevel`, `currentLevel`, `canLearn`, `blockedReason` (no description/icon)
- Monster sprites served via `/ro-assets/output-monsters/{jobId}_0_0.png` — requires zrenderer bat to have run first; front falls back to CDN (`ratemyserver.net`) then error icon
- NPC sprites: after running `ClientDataRunner` with `ro.assets.run-populator=true`, most NPCs will have `spriteUrl`. NPCs with jobIds outside the rendered range (45–899) will have a URL that returns 404 — frontend must handle this gracefully
- NPC dialog frontend: `GET /api/npcs/{npcId}/dialog` exists in backend but no UI wired yet
- NPC shop, Market, Trade, Cash Shop controllers exist but are not connected to antifraude fraud checks
- `AccountService.login()` — no logging of failed login attempts (security audit gap)

---

## Running

```bash
# Requires PostgreSQL running (default: localhost:5432/ragnarok_db, user: postgres, pass: postgre)
./mvnw spring-boot:run

# Tests (requires Docker Desktop for Testcontainers)
./mvnw test
```

Startup order in the ecosystem: **antifraude (8081) → core (8080) → front (3000)**
