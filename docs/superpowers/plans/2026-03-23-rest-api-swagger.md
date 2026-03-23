# REST API + Swagger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the full game loop as a REST API with Swagger UI, enabling anyone to interact with the game via browser without cloning the repo.

**Architecture:** Add a new `com.ragnarok.api` package with controllers, request/response DTOs, and a global exception handler. All controllers delegate to existing application services — zero business logic in the API layer. A new `MapService` is created to extract map navigation logic currently embedded in `RagnarokTerminalRunner`.

**Tech Stack:** Spring Boot 3.4.2, springdoc-openapi 2.8.6, MockMvc (`@WebMvcTest`) for controller tests.

---

## File Map

| Action | File |
|---|---|
| Modify | `pom.xml` |
| Create | `src/main/java/com/ragnarok/api/dto/response/PlayerResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/response/BattleResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/response/SkillUseResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/response/SkillRowResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/response/InventoryItemResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/response/MapInfoResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/response/WalkResponseDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/request/AttackRequestDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/request/UseSkillRequestDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/request/TravelRequestDTO.java` |
| Create | `src/main/java/com/ragnarok/api/dto/request/CreatePlayerRequestDTO.java` |
| Create | `src/main/java/com/ragnarok/api/GlobalExceptionHandler.java` |
| Create | `src/main/java/com/ragnarok/application/service/MapService.java` |
| Create | `src/main/java/com/ragnarok/api/controller/PlayerController.java` |
| Create | `src/main/java/com/ragnarok/api/controller/BattleController.java` |
| Create | `src/main/java/com/ragnarok/api/controller/SkillController.java` |
| Create | `src/main/java/com/ragnarok/api/controller/ItemController.java` |
| Create | `src/main/java/com/ragnarok/api/controller/MapController.java` |
| Modify | `pom.xml` (JaCoCo exclusions for controllers covered by WebMvcTest) |
| Create | `src/test/java/com/ragnarok/api/controller/PlayerControllerTest.java` |
| Create | `src/test/java/com/ragnarok/api/controller/BattleControllerTest.java` |
| Create | `src/test/java/com/ragnarok/api/controller/SkillControllerTest.java` |
| Create | `src/test/java/com/ragnarok/api/controller/ItemControllerTest.java` |
| Create | `src/test/java/com/ragnarok/api/controller/MapControllerTest.java` |

---

## Task 1: Add springdoc-openapi dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependency**

Inside `<dependencies>` in `pom.xml`, add:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

- [ ] **Step 2: Verify build compiles**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 3: Verify Swagger UI endpoint exists**

Start the app and access `http://localhost:8080/swagger-ui.html` — it should show an empty Swagger UI page (no endpoints yet).

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add springdoc-openapi 2.8.6 for Swagger UI"
```

---

## Task 2: Create response and request DTOs

**Files:**
- Create 8 response DTOs, 4 request DTOs

- [ ] **Step 1: Create response DTOs**

`src/main/java/com/ragnarok/api/dto/response/PlayerResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

public record PlayerResponseDTO(
        Long id,
        String name,
        String jobClass,
        Integer baseLevel,
        Integer jobLevel,
        Integer hpCurrent,
        Integer hpMax,
        Integer spCurrent,
        Integer spMax,
        Integer str,
        Integer agi,
        Integer vit,
        Integer intelligence,
        Integer dex,
        Integer luk,
        Integer statPoints,
        Integer skillPoints,
        Long zenny,
        String mapName
) {}
```

`src/main/java/com/ragnarok/api/dto/response/BattleResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

public record BattleResponseDTO(String message) {}
```

`src/main/java/com/ragnarok/api/dto/response/SkillUseResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

public record SkillUseResponseDTO(String message) {}
```

`src/main/java/com/ragnarok/api/dto/response/SkillRowResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

public record SkillRowResponseDTO(
        String aegisName,
        String name,
        Integer maxLevel,
        Integer currentLevel,
        Boolean canLearn,
        String blockedReason
) {}
```

`src/main/java/com/ragnarok/api/dto/response/InventoryItemResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

public record InventoryItemResponseDTO(
        String id,
        String name,
        String type,
        Integer amount,
        Boolean equipped
) {}
```

`src/main/java/com/ragnarok/api/dto/response/MapInfoResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

import java.util.List;

public record MapInfoResponseDTO(String currentMap, List<String> availablePortals) {}
```

`src/main/java/com/ragnarok/api/dto/response/WalkResponseDTO.java`:
```java
package com.ragnarok.api.dto.response;

public record WalkResponseDTO(boolean encounterOccurred, Long monsterId, String monsterName, Integer monsterHp, String message) {}
```

- [ ] **Step 2: Create request DTOs**

`src/main/java/com/ragnarok/api/dto/request/AttackRequestDTO.java`:
```java
package com.ragnarok.api.dto.request;

public record AttackRequestDTO(Long playerId, Long monsterId) {}
```

`src/main/java/com/ragnarok/api/dto/request/UseSkillRequestDTO.java`:
```java
package com.ragnarok.api.dto.request;

public record UseSkillRequestDTO(Long monsterId) {}
```

`src/main/java/com/ragnarok/api/dto/request/TravelRequestDTO.java`:
```java
package com.ragnarok.api.dto.request;

public record TravelRequestDTO(String destination) {}
```

`src/main/java/com/ragnarok/api/dto/request/CreatePlayerRequestDTO.java`:
```java
package com.ragnarok.api.dto.request;

public record CreatePlayerRequestDTO(String name, String jobClass) {}
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/api/
git commit -m "feat(api): add request and response DTOs"
```

---

## Task 3: Create GlobalExceptionHandler and MapService

**Files:**
- Create: `src/main/java/com/ragnarok/api/GlobalExceptionHandler.java`
- Create: `src/main/java/com/ragnarok/application/service/MapService.java`

- [ ] **Step 1: Create GlobalExceptionHandler**

`src/main/java/com/ragnarok/api/GlobalExceptionHandler.java`:
```java
package com.ragnarok.api;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.domain.exception.SkillNotFoundException;
import com.ragnarok.domain.exception.InsufficientSpException;
import com.ragnarok.domain.exception.InsufficientSkillPointsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSkillNotFound(SkillNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({PlayerDeadException.class, InsufficientSpException.class,
                        InsufficientSkillPointsException.class})
    public ResponseEntity<Map<String, String>> handleGameRuleViolation(GameException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<Map<String, String>> handleGame(GameException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
    }
}
```

- [ ] **Step 2: Create MapService**

This extracts the map navigation logic from `RagnarokTerminalRunner` (methods `caminhar()` and `iniciarEncontroAleatorio()`):

`src/main/java/com/ragnarok/application/service/MapService.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);

    private final PlayerRepository playerRepository;
    private final MapPortalRepository portalRepository;
    private final MapMonsterRepository mapMonsterRepository;
    private final MonsterRepository monsterRepository;
    private final Random rng = new Random();

    public MapService(PlayerRepository playerRepository,
                      MapPortalRepository portalRepository,
                      MapMonsterRepository mapMonsterRepository,
                      MonsterRepository monsterRepository) {
        this.playerRepository = playerRepository;
        this.portalRepository = portalRepository;
        this.mapMonsterRepository = mapMonsterRepository;
        this.monsterRepository = monsterRepository;
    }

    public String getCurrentMap(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        return player.getMapName() != null ? player.getMapName() : "prontera";
    }

    public List<String> getPortals(String mapId) {
        return portalRepository.findDestinosByMapFrom(mapId)
                .stream()
                .filter(d -> !d.equals(mapId))
                .toList();
    }

    @Transactional
    public void travel(Long playerId, String destination) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        String currentMap = player.getMapName() != null ? player.getMapName() : "prontera";
        List<String> available = getPortals(currentMap);
        if (!available.contains(destination)) {
            throw new GameException("Portal to " + destination + " not available from " + currentMap);
        }
        player.setMapName(destination);
        playerRepository.save(player);
    }

    /** Returns a random monster encounter on the player's current map, or null if no encounter. */
    public MonsterEntity walk(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        String map = player.getMapName() != null ? player.getMapName() : "prontera";

        if (rng.nextInt(100) >= 70) return null;  // 70% encounter chance

        List<MapMonsterEntity> entries = mapMonsterRepository.findByMapId(map);
        if (entries.isEmpty()) return null;

        int totalWeight = entries.stream().mapToInt(e -> e.getAmount() != null ? e.getAmount() : 1).sum();
        int roll = rng.nextInt(totalWeight);
        int accumulated = 0;
        MapMonsterEntity chosen = entries.get(0);
        for (MapMonsterEntity entry : entries) {
            accumulated += entry.getAmount() != null ? entry.getAmount() : 1;
            if (roll < accumulated) {
                chosen = entry;
                break;
            }
        }

        MonsterEntity monster = chosen.getMonster();
        if (monster.getHp() == null || monster.getHp() <= 0) {
            monster.setHp(100);
            monsterRepository.save(monster);
        }
        return monster;
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/api/GlobalExceptionHandler.java \
        src/main/java/com/ragnarok/application/service/MapService.java
git commit -m "feat(api): add GlobalExceptionHandler and MapService"
```

---

## Task 4: PlayerController with tests

**Files:**
- Create: `src/main/java/com/ragnarok/api/controller/PlayerController.java`
- Create: `src/test/java/com/ragnarok/api/controller/PlayerControllerTest.java`

- [ ] **Step 1: Write failing test**

`src/test/java/com/ragnarok/api/controller/PlayerControllerTest.java`:
```java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.dto.request.CreatePlayerRequestDTO;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PlayerRepository playerRepository;
    @MockBean PlayerService playerService;

    @Test
    void getPlayers_returnsEmptyList() throws Exception {
        when(playerRepository.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/players"))
           .andExpect(status().isOk())
           .andExpect(content().json("[]"));
    }

    @Test
    void getPlayer_notFound_returns404() throws Exception {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/players/99"))
           .andExpect(status().isNotFound());
    }

    @Test
    void getPlayer_found_returnsPlayer() throws Exception {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(1L);
        entity.setName("Hero");
        entity.setJobClass("NOVICE");
        entity.setBaseLevel(1);
        entity.setHpCurrent(100);
        entity.setHpMax(100);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(entity));

        mvc.perform(get("/api/players/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("Hero"))
           .andExpect(jsonPath("$.jobClass").value("NOVICE"));
    }

    @Test
    void createPlayer_returnsCreated() throws Exception {
        Player created = new Player();
        created.setId(1L);
        created.setName("Hero");
        when(playerService.criarNovoPersonagem("Hero", "NOVICE")).thenReturn(created);

        mvc.perform(post("/api/players")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new CreatePlayerRequestDTO("Hero", "NOVICE"))))
           .andExpect(status().isCreated());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw test -Dtest=PlayerControllerTest -q
```

Expected: FAIL — `PlayerController` does not exist yet.

- [ ] **Step 3: Implement PlayerController**

`src/main/java/com/ragnarok/api/controller/PlayerController.java`:
```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.CreatePlayerRequestDTO;
import com.ragnarok.api.dto.response.PlayerResponseDTO;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players", description = "Player management")
public class PlayerController {

    private final PlayerRepository playerRepository;
    private final PlayerService playerService;

    public PlayerController(PlayerRepository playerRepository, PlayerService playerService) {
        this.playerRepository = playerRepository;
        this.playerService = playerService;
    }

    @GetMapping
    @Operation(summary = "List all players")
    public List<PlayerResponseDTO> listPlayers() {
        return playerRepository.findAll().stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player by ID")
    public ResponseEntity<PlayerResponseDTO> getPlayer(@PathVariable Long id) {
        return playerRepository.findById(id)
                .map(e -> ResponseEntity.ok(toDTO(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a new player")
    public ResponseEntity<PlayerResponseDTO> createPlayer(@RequestBody CreatePlayerRequestDTO req) {
        Player created = playerService.criarNovoPersonagem(req.name(), req.jobClass());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlayerResponseDTO(created.getId(), created.getName(),
                        created.getJobClass(), created.getBaseLevel(), created.getJobLevel(),
                        created.getHpCurrent(), created.getHpMax(), created.getSpCurrent(), created.getSpMax(),
                        created.getStats() != null ? created.getStats().str() : null,
                        created.getStats() != null ? created.getStats().agi() : null,
                        created.getStats() != null ? created.getStats().vit() : null,
                        created.getStats() != null ? created.getStats().intelligence() : null,
                        created.getStats() != null ? created.getStats().dex() : null,
                        created.getStats() != null ? created.getStats().luk() : null,
                        null, null, created.getZenny(), null));
    }

    private PlayerResponseDTO toDTO(PlayerEntity e) {
        return new PlayerResponseDTO(
                e.getId(), e.getName(), e.getJobClass(),
                e.getBaseLevel(), e.getJobLevel(),
                e.getHpCurrent(), e.getHpMax(),
                e.getSpCurrent(), e.getSpMax(),
                e.getStr(), e.getAgi(), e.getVit(), e.getIntelligence(),
                e.getDex(), e.getLuk(),
                e.getStatPoints(), e.getSkillPoints(),
                e.getZenny(), e.getMapName()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw test -Dtest=PlayerControllerTest -q
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/PlayerController.java \
        src/test/java/com/ragnarok/api/controller/PlayerControllerTest.java
git commit -m "feat(api): add PlayerController with GET /api/players and POST /api/players"
```

---

## Task 5: BattleController with tests

**Files:**
- Create: `src/main/java/com/ragnarok/api/controller/BattleController.java`
- Create: `src/test/java/com/ragnarok/api/controller/BattleControllerTest.java`

- [ ] **Step 1: Write failing test**

`src/test/java/com/ragnarok/api/controller/BattleControllerTest.java`:
```java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.application.service.BattleService;
import com.ragnarok.domain.exception.PlayerDeadException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BattleController.class)
class BattleControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean BattleService battleService;

    @Test
    void attack_returnsMessage() throws Exception {
        when(battleService.realizarAtaque(1L, 2L)).thenReturn("ATAQUE: causou 45 de dano.");

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("ATAQUE: causou 45 de dano."));
    }

    @Test
    void attack_playerDead_returns400() throws Exception {
        when(battleService.realizarAtaque(1L, 2L)).thenThrow(new PlayerDeadException());

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void attack_playerNotFound_returns404() throws Exception {
        when(battleService.realizarAtaque(99L, 2L))
                .thenThrow(new IllegalArgumentException("Player not found"));

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(99L, 2L))))
           .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./mvnw test -Dtest=BattleControllerTest -q
```

- [ ] **Step 3: Implement BattleController**

`src/main/java/com/ragnarok/api/controller/BattleController.java`:
```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.api.dto.response.BattleResponseDTO;
import com.ragnarok.application.service.BattleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/battle")
@Tag(name = "Battle", description = "Combat actions")
public class BattleController {

    private final BattleService battleService;

    public BattleController(BattleService battleService) {
        this.battleService = battleService;
    }

    @PostMapping("/attack")
    @Operation(summary = "Perform a basic attack against a monster")
    public ResponseEntity<BattleResponseDTO> attack(@RequestBody AttackRequestDTO req) {
        String result = battleService.realizarAtaque(req.playerId(), req.monsterId());
        return ResponseEntity.ok(new BattleResponseDTO(result));
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./mvnw test -Dtest=BattleControllerTest -q
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/BattleController.java \
        src/test/java/com/ragnarok/api/controller/BattleControllerTest.java
git commit -m "feat(api): add BattleController with POST /api/battle/attack"
```

---

## Task 6: SkillController with tests

**Files:**
- Create: `src/main/java/com/ragnarok/api/controller/SkillController.java`
- Create: `src/test/java/com/ragnarok/api/controller/SkillControllerTest.java`

- [ ] **Step 1: Write failing test**

`src/test/java/com/ragnarok/api/controller/SkillControllerTest.java`:
```java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.dto.request.UseSkillRequestDTO;
import com.ragnarok.application.dto.SkillRowDTO;
import com.ragnarok.application.service.SkillCombatService;
import com.ragnarok.application.service.SkillService;
import com.ragnarok.domain.exception.InsufficientSpException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SkillService skillService;
    @MockBean SkillCombatService skillCombatService;

    @Test
    void listSkills_returnsList() throws Exception {
        when(skillService.listarSkillsDoPlayer(1L)).thenReturn(
                List.of(new SkillRowDTO("SM_BASH", "Bash", 10, 1, true, null)));

        mvc.perform(get("/api/players/1/skills"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].aegisName").value("SM_BASH"));
    }

    @Test
    void learnSkill_returnsMessage() throws Exception {
        when(skillService.aprenderSkill(1L, "SM_BASH")).thenReturn("SM_BASH agora está no nível 2");

        mvc.perform(post("/api/players/1/skills/SM_BASH/learn"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("SM_BASH agora está no nível 2"));
    }

    @Test
    void useSkill_insufficientSp_returns400() throws Exception {
        when(skillCombatService.usarSkillEmCombate(1L, "SM_BASH", 2L))
                .thenThrow(new InsufficientSpException(20, 5));

        mvc.perform(post("/api/players/1/skills/SM_BASH/use")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new UseSkillRequestDTO(2L))))
           .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./mvnw test -Dtest=SkillControllerTest -q
```

- [ ] **Step 3: Implement SkillController**

`src/main/java/com/ragnarok/api/controller/SkillController.java`:
```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.UseSkillRequestDTO;
import com.ragnarok.api.dto.response.SkillRowResponseDTO;
import com.ragnarok.api.dto.response.SkillUseResponseDTO;
import com.ragnarok.application.dto.SkillRowDTO;
import com.ragnarok.application.service.SkillCombatService;
import com.ragnarok.application.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players/{playerId}/skills")
@Tag(name = "Skills", description = "Skill tree and combat skill usage")
public class SkillController {

    private final SkillService skillService;
    private final SkillCombatService skillCombatService;

    public SkillController(SkillService skillService, SkillCombatService skillCombatService) {
        this.skillService = skillService;
        this.skillCombatService = skillCombatService;
    }

    @GetMapping
    @Operation(summary = "List all skills available to the player")
    public List<SkillRowResponseDTO> listSkills(@PathVariable Long playerId) {
        return skillService.listarSkillsDoPlayer(playerId).stream()
                .map(this::toDTO).toList();
    }

    @PostMapping("/{skillName}/learn")
    @Operation(summary = "Learn or level up a skill")
    public ResponseEntity<SkillUseResponseDTO> learnSkill(
            @PathVariable Long playerId, @PathVariable String skillName) {
        String result = skillService.aprenderSkill(playerId, skillName);
        return ResponseEntity.ok(new SkillUseResponseDTO(result));
    }

    @PostMapping("/{skillName}/use")
    @Operation(summary = "Use a skill (optionally targeting a monster)")
    public ResponseEntity<SkillUseResponseDTO> useSkill(
            @PathVariable Long playerId,
            @PathVariable String skillName,
            @RequestBody(required = false) UseSkillRequestDTO req) {
        Long monsterId = req != null ? req.monsterId() : null;
        String result = skillCombatService.usarSkillEmCombate(playerId, skillName, monsterId);
        return ResponseEntity.ok(new SkillUseResponseDTO(result));
    }

    private SkillRowResponseDTO toDTO(SkillRowDTO dto) {
        return new SkillRowResponseDTO(dto.aegisName(), dto.name(),
                dto.maxLevel(), dto.currentLevel(), dto.canLearn(), dto.blockedReason());
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./mvnw test -Dtest=SkillControllerTest -q
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/SkillController.java \
        src/test/java/com/ragnarok/api/controller/SkillControllerTest.java
git commit -m "feat(api): add SkillController"
```

---

## Task 7: ItemController and MapController with tests

**Files:**
- Create: `src/main/java/com/ragnarok/api/controller/ItemController.java`
- Create: `src/main/java/com/ragnarok/api/controller/MapController.java`
- Create: `src/test/java/com/ragnarok/api/controller/ItemControllerTest.java`
- Create: `src/test/java/com/ragnarok/api/controller/MapControllerTest.java`

- [ ] **Step 1: Write failing tests**

`src/test/java/com/ragnarok/api/controller/ItemControllerTest.java`:
```java
package com.ragnarok.api.controller;

import com.ragnarok.application.service.ItemService;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
class ItemControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PlayerItemRepository playerItemRepository;
    @MockBean ItemService itemService;

    @Test
    void getInventory_returnsList() throws Exception {
        ItemEntity item = new ItemEntity();
        item.setId(1L);
        item.setName("Red Potion");
        item.setType(ItemType.CONSUMABLE);

        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setId(UUID.randomUUID());
        pi.setItem(item);
        pi.setAmount(3);
        pi.setEquipped(false);

        when(playerItemRepository.findByPlayerId(1L)).thenReturn(List.of(pi));

        mvc.perform(get("/api/players/1/inventory"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Red Potion"))
           .andExpect(jsonPath("$[0].amount").value(3));
    }

    @Test
    void useItem_returnsMessage() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(itemService.usarItem(itemId)).thenReturn("Você usou Red Potion e recuperou 45 HP.");

        mvc.perform(post("/api/players/1/inventory/" + itemId + "/use"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("Você usou Red Potion e recuperou 45 HP."));
    }
}
```

`src/test/java/com/ragnarok/api/controller/MapControllerTest.java`:
```java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.dto.request.TravelRequestDTO;
import com.ragnarok.application.service.MapService;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapController.class)
class MapControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MapService mapService;

    @Test
    void getMapInfo_returnsCurrentMapAndPortals() throws Exception {
        when(mapService.getCurrentMap(1L)).thenReturn("prontera");
        when(mapService.getPortals("prontera")).thenReturn(List.of("izlude", "geffen"));

        mvc.perform(get("/api/players/1/map"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.currentMap").value("prontera"))
           .andExpect(jsonPath("$.availablePortals[0]").value("izlude"));
    }

    @Test
    void walk_encounterOccurred_returnsMonsterInfo() throws Exception {
        MonsterEntity monster = new MonsterEntity();
        monster.setId(1002L);
        monster.setName("Poring");
        monster.setHp(55);
        when(mapService.walk(1L)).thenReturn(monster);

        mvc.perform(post("/api/players/1/map/walk"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.encounterOccurred").value(true))
           .andExpect(jsonPath("$.monsterName").value("Poring"));
    }

    @Test
    void walk_noEncounter_returnsEncounterFalse() throws Exception {
        when(mapService.walk(1L)).thenReturn(null);

        mvc.perform(post("/api/players/1/map/walk"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.encounterOccurred").value(false));
    }

    @Test
    void travel_returnsOk() throws Exception {
        mvc.perform(post("/api/players/1/map/travel")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new TravelRequestDTO("izlude"))))
           .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./mvnw test -Dtest=ItemControllerTest,MapControllerTest -q
```

- [ ] **Step 3: Implement ItemController**

`src/main/java/com/ragnarok/api/controller/ItemController.java`:
```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.response.InventoryItemResponseDTO;
import com.ragnarok.api.dto.response.SkillUseResponseDTO;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/players/{playerId}/inventory")
@Tag(name = "Inventory", description = "Player inventory management")
public class ItemController {

    private final PlayerItemRepository playerItemRepository;
    private final ItemService itemService;

    public ItemController(PlayerItemRepository playerItemRepository, ItemService itemService) {
        this.playerItemRepository = playerItemRepository;
        this.itemService = itemService;
    }

    @GetMapping
    @Operation(summary = "List player inventory")
    public List<InventoryItemResponseDTO> getInventory(@PathVariable Long playerId) {
        return playerItemRepository.findByPlayerId(playerId).stream()
                .map(this::toDTO).toList();
    }

    @PostMapping("/{itemId}/use")
    @Operation(summary = "Use an item from inventory")
    public ResponseEntity<SkillUseResponseDTO> useItem(
            @PathVariable Long playerId, @PathVariable UUID itemId) {
        String result = itemService.usarItem(itemId);
        return ResponseEntity.ok(new SkillUseResponseDTO(result));
    }

    private InventoryItemResponseDTO toDTO(PlayerItemEntity pi) {
        return new InventoryItemResponseDTO(
                pi.getId().toString(),
                pi.getItem() != null ? pi.getItem().getName() : "Unknown",
                pi.getItem() != null && pi.getItem().getType() != null
                        ? pi.getItem().getType().name() : "UNKNOWN",
                pi.getAmount(),
                pi.getEquipped()
        );
    }
}
```

- [ ] **Step 4: Implement MapController**

`src/main/java/com/ragnarok/api/controller/MapController.java`:
```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.TravelRequestDTO;
import com.ragnarok.api.dto.response.MapInfoResponseDTO;
import com.ragnarok.api.dto.response.WalkResponseDTO;
import com.ragnarok.application.service.MapService;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players/{playerId}/map")
@Tag(name = "Map", description = "World navigation")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping
    @Operation(summary = "Get current map and available portals")
    public MapInfoResponseDTO getMapInfo(@PathVariable Long playerId) {
        String map = mapService.getCurrentMap(playerId);
        return new MapInfoResponseDTO(map, mapService.getPortals(map));
    }

    @PostMapping("/walk")
    @Operation(summary = "Walk in the current map — may trigger a monster encounter (70% chance)")
    public ResponseEntity<WalkResponseDTO> walk(@PathVariable Long playerId) {
        MonsterEntity monster = mapService.walk(playerId);
        if (monster == null) {
            return ResponseEntity.ok(new WalkResponseDTO(false, null, null, null, "No encounter."));
        }
        return ResponseEntity.ok(new WalkResponseDTO(true, monster.getId(),
                monster.getName(), monster.getHp(), monster.getName() + " appeared!"));
    }

    @PostMapping("/travel")
    @Operation(summary = "Travel to another map via portal")
    public ResponseEntity<Void> travel(@PathVariable Long playerId, @RequestBody TravelRequestDTO req) {
        mapService.travel(playerId, req.destination());
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 5: Run all tests — expect PASS**

```bash
./mvnw test -Dtest=ItemControllerTest,MapControllerTest -q
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run full test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS` — all 212+ tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/ \
        src/test/java/com/ragnarok/api/controller/
git commit -m "feat(api): add ItemController and MapController — REST API complete"
```

---

## Task 8: Final verification

- [ ] **Step 1: Start application and verify Swagger UI**

```bash
java -jar target/ragnarok-core-0.0.1-SNAPSHOT.jar
```

Open browser: `http://localhost:8080/swagger-ui.html`

Expected: Swagger UI shows 5 controller groups (Players, Battle, Skills, Inventory, Map) with all endpoints listed.

- [ ] **Step 2: Run full test suite with coverage**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`, JaCoCo ≥ 85% line, ≥ 62% branch.

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat(api): REST API + Swagger UI complete — all endpoints exposed"
```
