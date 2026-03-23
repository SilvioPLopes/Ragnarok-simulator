# Spring Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple `BattleService` from loot persistence, XP processing, and player death handling by publishing domain events. New consequences of battles can be added by implementing a new `@EventListener` without touching `BattleService`.

**Architecture:** Three immutable domain event records in `com.ragnarok.domain.event`. `BattleService` calculates loot (using its already-injected `BattleEngine`), then publishes events with the extracted data — not full aggregates. A new `BattleEventHandler` in the application layer holds all `@EventListener` methods and delegates to existing services. `LevelingService` stays pure domain (no Spring annotations). `RagnarokTerminalRunner.handlePlayerDeath()` is simplified — the resurrection and map-reset logic moves to `BattleEventHandler`.

**IMPORTANT — Wave 2 dependency:** This plan must be executed AFTER the REST API plan (Wave 1) is complete and all tests pass. `BattleService` is the most central service — refactoring it after the API is stable reduces regression risk.

**Tech Stack:** Spring `ApplicationEventPublisher`, `@EventListener`, `@Component`. No new dependencies.

---

## File Map

| Action | File |
|---|---|
| Create | `src/main/java/com/ragnarok/domain/event/MonsterKilledEvent.java` |
| Create | `src/main/java/com/ragnarok/domain/event/PlayerLeveledUpEvent.java` |
| Create | `src/main/java/com/ragnarok/domain/event/PlayerDiedEvent.java` |
| Create | `src/main/java/com/ragnarok/application/service/BattleEventHandler.java` |
| Modify | `src/main/java/com/ragnarok/application/service/BattleService.java` |
| Modify | `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java` |
| Create | `src/test/java/com/ragnarok/application/service/BattleEventHandlerTest.java` |

---

## Task 1: Create domain event records

**Files:**
- Create 3 event records in `src/main/java/com/ragnarok/domain/event/`

Domain events are pure Java records with no Spring dependencies. They live in the domain layer.

`MonsterKilledEvent` carries only the data that listeners need — pre-extracted by `BattleService` before publishing. This avoids passing full aggregates through the event bus and keeps listeners simple.

- [ ] **Step 1: Create event records**

`src/main/java/com/ragnarok/domain/event/MonsterKilledEvent.java`:
```java
package com.ragnarok.domain.event;

import com.ragnarok.domain.model.Item;

import java.util.List;

/**
 * Published when a player defeats a monster.
 *
 * Carries pre-calculated loot and XP — BattleService computes these
 * before publishing so listeners never need to re-load the monster aggregate.
 * monsterId is included so future listeners can reference the monster without reloading.
 */
public record MonsterKilledEvent(
        Long playerId,
        Long monsterId,
        List<Item> loot,
        long baseExp,
        long jobExp
) {}
```

`src/main/java/com/ragnarok/domain/event/PlayerLeveledUpEvent.java`:
```java
package com.ragnarok.domain.event;

/**
 * Published when a player gains a base or job level.
 * Published by BattleEventHandler after XP processing confirms a level change.
 */
public record PlayerLeveledUpEvent(
        Long playerId,
        int newBaseLevel,
        int newJobLevel
) {}
```

`src/main/java/com/ragnarok/domain/event/PlayerDiedEvent.java`:
```java
package com.ragnarok.domain.event;

/**
 * Published when a player's HP reaches 0 during combat.
 * Listeners handle resurrection and map reset.
 */
public record PlayerDiedEvent(Long playerId) {}
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ragnarok/domain/event/
git commit -m "feat(events): add MonsterKilledEvent, PlayerLeveledUpEvent, PlayerDiedEvent domain records"
```

---

## Task 2: Create BattleEventHandler

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/BattleEventHandler.java`

`BattleEventHandler` holds all `@EventListener` methods. It:
- Handles `MonsterKilledEvent` → persists loot drops + processes XP/level via `LevelingService` + publishes `PlayerLeveledUpEvent` if player leveled up
- Handles `PlayerDiedEvent` → resurrects player + resets map to prontera

This class replicates the logic currently in `BattleService.processarMorteMonstro()` and `RagnarokTerminalRunner.handlePlayerDeath()`.

**IMPORTANT:** Spring's synchronous `ApplicationEventPublisher` fires listeners in the same thread and transaction as the publisher. This means loot and XP are persisted atomically within `BattleService.realizarAtaque()`'s `@Transactional` boundary — same behavior as before.

**IMPORTANT:** `BattleEventHandler` does NOT instantiate `BattleEngine` directly. Loot is pre-calculated by `BattleService` (which already has `BattleEngine` injected) and arrives in `MonsterKilledEvent.loot()`. `BattleEventHandler` only needs repositories, mappers, and `LevelingService`.

- [ ] **Step 1: Read BattleService.processarMorteMonstro(), RagnarokTerminalRunner.handlePlayerDeath(), and ItemMapper**

Read these three files before writing `BattleEventHandler`:
1. `src/main/java/com/ragnarok/application/service/BattleService.java` lines 120–188 — current loot-persistence logic
2. `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java` lines 658–667 — current resurrection logic
3. `src/main/java/com/ragnarok/infrastructure/client/mapper/ItemMapper.java` — **verify whether `toEntity(Item)` exists**

**CRITICAL:** If `ItemMapper` only maps entity→domain (i.e., `toEntity()` does NOT exist), the loot-persistence code in Step 4 must use `ItemRepository.findById(itemDomain.getId())` instead of `itemMapper.toEntity(itemDomain)`. The implementation code in Step 4 shows both variations — choose the one that matches what you find.

- [ ] **Step 2: Write failing test**

`src/test/java/com/ragnarok/application/service/BattleEventHandlerTest.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleEventHandlerTest {

    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    // NOTE: If Step 1 reveals ItemMapper.toEntity() does NOT exist and you use ItemRepository
    //       in the implementation instead, replace the next line with:
    //       @Mock ItemRepository itemRepository;
    @Mock ItemMapper itemMapper;
    @Mock PlayerMapper playerMapper;
    @Mock LevelingService levelingService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BattleEventHandler handler;

    @Test
    void onMonsterKilled_persistsXpAndCallsLeveling() {
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setId(1L);
        playerEntity.setHpMax(100);
        playerEntity.setHpCurrent(80);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(new com.ragnarok.domain.model.Player());
        when(levelingService.processarExperiencia(any(), anyLong(), anyLong())).thenReturn("Sem level up.");

        handler.onMonsterKilled(new MonsterKilledEvent(1L, 999L, List.of(), 40L, 20L));

        verify(levelingService).processarExperiencia(any(), eq(40L), eq(20L));
        verify(playerRepository).save(playerEntity);
    }

    @Test
    void onPlayerDied_resurrectionAndMapReset() {
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setId(1L);
        playerEntity.setHpMax(200);
        playerEntity.setHpCurrent(0);
        playerEntity.setMapName("geffen");

        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerEntity));

        handler.onPlayerDied(new PlayerDiedEvent(1L));

        verify(playerRepository).save(playerEntity);
        assertEquals(200, playerEntity.getHpCurrent());
        assertEquals("prontera", playerEntity.getMapName());
    }
}
```

- [ ] **Step 3: Run test — expect FAIL**

```bash
./mvnw test -Dtest=BattleEventHandlerTest -q
```

Expected: FAIL — `BattleEventHandler` does not exist.

- [ ] **Step 4: Implement BattleEventHandler**

`src/main/java/com/ragnarok/application/service/BattleEventHandler.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.event.PlayerLeveledUpEvent;
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles domain events published by BattleService.
 *
 * All event listeners are synchronous (Spring's default) — they execute
 * within the same transaction as the publisher, preserving atomicity.
 *
 * This class is the single place to add new battle consequences
 * without modifying BattleService.
 */
@Component
public class BattleEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BattleEventHandler.class);

    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final ItemMapper itemMapper;
    private final PlayerMapper playerMapper;
    private final LevelingService levelingService;
    private final ApplicationEventPublisher eventPublisher;

    public BattleEventHandler(PlayerRepository playerRepository,
                               PlayerItemRepository playerItemRepository,
                               ItemMapper itemMapper,
                               PlayerMapper playerMapper,
                               LevelingService levelingService,
                               ApplicationEventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemMapper = itemMapper;
        this.playerMapper = playerMapper;
        this.levelingService = levelingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles monster death: persists pre-calculated loot drops and processes XP/level up.
     * Loot is pre-calculated by BattleService before the event is published.
     * Publishes PlayerLeveledUpEvent if the player gained a level.
     */
    @EventListener
    public void onMonsterKilled(MonsterKilledEvent event) {
        PlayerEntity playerEntity = playerRepository.findById(event.playerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + event.playerId()));

        // 1. Persist loot drops (pre-calculated by BattleService via BattleEngine)
        // NOTE: If ItemMapper.toEntity() does NOT exist, replace `itemMapper.toEntity(itemDomain)`
        //       with `itemRepository.findById(itemDomain.getId()).orElseThrow(...)` and inject
        //       ItemRepository in the constructor instead of ItemMapper.
        for (Item itemDomain : event.loot()) {
            ItemEntity itemEntity = itemMapper.toEntity(itemDomain);
            List<PlayerItemEntity> existing =
                    playerItemRepository.findByPlayerIdAndItemId(playerEntity.getId(), itemEntity.getId());
            if (!existing.isEmpty()) {
                PlayerItemEntity stack = existing.get(0);
                int total = existing.stream().mapToInt(e -> e.getAmount() != null ? e.getAmount() : 1).sum();
                stack.setAmount(total + 1);
                playerItemRepository.save(stack);
                if (existing.size() > 1) {
                    playerItemRepository.deleteAll(existing.subList(1, existing.size()));
                }
            } else {
                PlayerItemEntity newItem = new PlayerItemEntity();
                newItem.setPlayer(playerEntity);
                newItem.setItem(itemEntity);
                newItem.setAmount(1);
                newItem.setRefineLevel(0);
                newItem.setEquipped(false);
                playerItemRepository.save(newItem);
            }
        }

        // 2. Process XP and level up (delegates to domain service)
        int baseLevelBefore = playerEntity.getBaseLevel() != null ? playerEntity.getBaseLevel() : 1;
        int jobLevelBefore  = playerEntity.getJobLevel()  != null ? playerEntity.getJobLevel()  : 1;

        Player playerDomain = playerMapper.toDomain(playerEntity);
        String levelLog = levelingService.processarExperiencia(playerDomain, event.baseExp(), event.jobExp());
        log.info("XP processed for player {}: {}", event.playerId(), levelLog);

        // 3. Persist updated level/exp/points/HP back to entity
        playerEntity.setBaseLevel(playerDomain.getBaseLevel());
        playerEntity.setJobLevel(playerDomain.getJobLevel());
        playerEntity.setBaseExp(playerDomain.getBaseExp());
        playerEntity.setJobExp(playerDomain.getJobExp());
        playerEntity.setStatPoints(playerDomain.getStatPoints());
        playerEntity.setSkillPoints(playerDomain.getSkillPoints());
        playerEntity.setHpCurrent(playerDomain.getHpCurrent());
        playerEntity.setSpCurrent(playerDomain.getSpCurrent());
        playerRepository.save(playerEntity);

        // 4. Publish PlayerLeveledUpEvent if a level was gained
        boolean leveled = playerDomain.getBaseLevel() > baseLevelBefore
                       || playerDomain.getJobLevel()  > jobLevelBefore;
        if (leveled) {
            log.info("Player {} leveled up! Base={}, Job={}.",
                    event.playerId(), playerDomain.getBaseLevel(), playerDomain.getJobLevel());
            eventPublisher.publishEvent(new PlayerLeveledUpEvent(
                    event.playerId(), playerDomain.getBaseLevel(), playerDomain.getJobLevel()));
        }
    }

    /**
     * Handles player death: resurrects at prontera with full HP.
     */
    @EventListener
    public void onPlayerDied(PlayerDiedEvent event) {
        PlayerEntity playerEntity = playerRepository.findById(event.playerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + event.playerId()));

        int maxHp = playerEntity.getHpMax() != null ? playerEntity.getHpMax() : 100;
        playerEntity.setHpCurrent(maxHp);
        playerEntity.setMapName("prontera");
        playerRepository.save(playerEntity);

        log.info("Player {} resurrected at prontera.", event.playerId());
    }
}
```

- [ ] **Step 5: Run test — expect PASS**

```bash
./mvnw test -Dtest=BattleEventHandlerTest -q
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/BattleEventHandler.java \
        src/test/java/com/ragnarok/application/service/BattleEventHandlerTest.java
git commit -m "feat(events): add BattleEventHandler with @EventListener for monster kill and player death"
```

---

## Task 3: Refactor BattleService to publish events

**Files:**
- Modify: `src/main/java/com/ragnarok/application/service/BattleService.java`

- [ ] **Step 1: Read current BattleService before editing**

Read `src/main/java/com/ragnarok/application/service/BattleService.java` fully. The key method is `realizarAtaque()` (lines 61–118) and `processarMorteMonstro()` (lines 120–188).

- [ ] **Step 2: Run existing BattleService tests before changes**

```bash
./mvnw test -Dtest=BattleServiceTest,BattleIntegrationTest,BattleLootIntegrationTest -q
```

Expected: all pass. This is your baseline.

- [ ] **Step 3: Refactor BattleService**

Changes:
1. Add `ApplicationEventPublisher` field and constructor parameter
2. When monster HP reaches 0: calculate loot using the already-injected `battleEngine.calculateLoot(monster)`, then publish `MonsterKilledEvent(playerId, loot, monster.getBaseExp(), monster.getJobExp())`
3. When player HP reaches 0 after counter-attack: publish `PlayerDiedEvent(playerId)`
4. Remove `processarMorteMonstro()` method (logic is now in `BattleEventHandler`)
5. Remove unused imports and constructor parameters **only if** they are solely used by `processarMorteMonstro()`. Check all usages before removing.

Updated `BattleService.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    private final PlayerRepository playerRepository;
    private final MonsterRepository monsterRepository;
    private final PlayerMapper playerMapper;
    private final MonsterMapper monsterMapper;
    private final BattleEngine battleEngine;
    private final WeaponSizeService weaponSizeService;
    private final ApplicationEventPublisher eventPublisher;

    public BattleService(PlayerRepository playerRepository,
                         MonsterRepository monsterRepository,
                         PlayerMapper playerMapper,
                         MonsterMapper monsterMapper,
                         BattleEngine battleEngine,
                         WeaponSizeService weaponSizeService,
                         ApplicationEventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.monsterRepository = monsterRepository;
        this.playerMapper = playerMapper;
        this.monsterMapper = monsterMapper;
        this.battleEngine = battleEngine;
        this.weaponSizeService = weaponSizeService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String realizarAtaque(Long playerId, Long monsterId) {
        PlayerEntity playerEntity = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                .orElseThrow(() -> new IllegalArgumentException("Monster not found"));

        if (playerEntity.getHpCurrent() != null && playerEntity.getHpCurrent() <= 0) {
            log.warn("Tentativa de ataque bloqueada: player {} está morto.", playerId);
            throw new PlayerDeadException();
        }

        Player player = playerMapper.toDomain(playerEntity);
        Monster monster = monsterMapper.toDomain(monsterEntity);

        // Calculate damage
        int damage = battleEngine.calculateDamage(player, monster);
        WeaponType weaponType = player.getEquipments().stream()
                .map(i -> i.getItemDefinition() != null ? i.getItemDefinition().getWeaponType() : null)
                .filter(wt -> wt != null && wt != WeaponType.NONE)
                .findFirst()
                .orElse(WeaponType.NONE);
        int sizeModPct = weaponSizeService.getModifier(weaponType, monster.getSize());
        damage = battleEngine.applyWeaponSizeModifier(damage, sizeModPct);

        // Apply damage to monster
        int newHp = Math.max(0, monsterEntity.getHp() - damage);
        monsterEntity.setHp(newHp);
        monsterRepository.save(monsterEntity);

        // Monster dies
        if (newHp <= 0) {
            log.info("Player {} derrotou {}.", playerId, monster.getName());
            // Pre-calculate loot here (BattleEngine is already injected)
            List<Item> loot = battleEngine.calculateLoot(monster);
            long baseExp = monster.getBaseExp() != null ? monster.getBaseExp() : 0;
            long jobExp  = monster.getJobExp()  != null ? monster.getJobExp()  : 0;
            // Publish event — BattleEventHandler persists loot, processes XP (synchronous, same transaction)
            eventPublisher.publishEvent(new MonsterKilledEvent(playerId, monsterId, loot, baseExp, jobExp));
            return "\uD83C\uDF1F VITÓRIA! O " + monster.getName() + " foi derrotado.";
        }

        // Monster counter-attack
        int monsterDamage = battleEngine.calculateMonsterDamage(monster, player);
        int playerNewHp = Math.max(0, playerEntity.getHpCurrent() - monsterDamage);
        playerEntity.setHpCurrent(playerNewHp);

        // Decrement active buffs
        player.decrementarBuffs();
        playerEntity.setActiveBuffsJson(playerMapper.serializeBuffs(player));
        playerRepository.save(playerEntity);

        if (playerNewHp <= 0) {
            log.info("Player {} morreu para {}.", playerId, monster.getName());
            // Publish event — BattleEventHandler handles resurrection and map reset (synchronous)
            eventPublisher.publishEvent(new PlayerDiedEvent(playerId));
            return String.format("FATAL: Você causou %d de dano, mas o %s contra-atacou com %d e você morreu.",
                    damage, monster.getName(), monsterDamage);
        }

        String arma = identificarArma(player);
        return String.format("ATAQUE: Voce causou %d de dano no %s com %s. (HP restante: %d)\n  >> %s contra-atacou e causou %d de dano em voce!",
                damage, monster.getName(), arma, newHp, monster.getName(), monsterDamage);
    }

    private String identificarArma(Player p) {
        return p.getInventory().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsEquipped()))
                .findFirst()
                .map(i -> i.getName() + " (ATK " + i.getItemDefinition().getStats().getAttack() + ")")
                .orElse("Punhos Nus");
    }
}
```

**Note on removed constructor parameters:** `LevelingService`, `ItemMapper`, and `PlayerItemRepository` were previously in `BattleService` only for `processarMorteMonstro()`. They are now removed from `BattleService` and owned by `BattleEventHandler`. If the current `BattleServiceTest` mocks these, remove those mocks — `UnnecessaryStubbingException` will tell you.

**Note on VITÓRIA message:** The existing terminal runner checks `resultado.contains("VITORIA") || resultado.contains("VITÓRIA")`. The new return value `"\uD83C\uDF1F VITÓRIA! ..."` still contains "VITÓRIA" — the check still works.

- [ ] **Step 4: Verify compilation**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Run BattleService tests**

```bash
./mvnw test -Dtest=BattleServiceTest,BattleIntegrationTest,BattleLootIntegrationTest -q
```

Fix any test failures — common issues:
- `BattleServiceTest`: remove `@Mock LevelingService`, `@Mock ItemMapper`, `@Mock PlayerItemRepository` and any stubs for them — they're no longer in `BattleService`'s constructor. Add `@Mock ApplicationEventPublisher eventPublisher` instead.
- `BattleLootIntegrationTest`: loot is now persisted by `BattleEventHandler`. Since the event fires synchronously in the same transaction, the `player_items` table should still be populated. If not, ensure the Spring context loads `BattleEventHandler` (it's `@Component` — it will be auto-detected).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/BattleService.java
git commit -m "feat(events): BattleService publishes MonsterKilledEvent and PlayerDiedEvent"
```

---

## Task 4: Simplify RagnarokTerminalRunner.handlePlayerDeath()

**Files:**
- Modify: `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java`

The resurrection and map reset logic is now handled by `BattleEventHandler.onPlayerDied()`. The terminal runner only needs to update UI state (reset `inBattle`, clear `currentMonster`).

- [ ] **Step 1: Read handlePlayerDeath() before editing**

Read `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java` lines 658–667.

Current implementation:
```java
private void handlePlayerDeath() {
    inBattle = false;
    currentMonster = null;
    System.out.println("\n>>> VOCE MORREU! Ressuscitando em Prontera...");
    playerService.ressuscitarJogador(currentPlayer.getId());
    PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow(...);
    p.setMapName("prontera");
    playerRepo.save(p);
    System.out.println(">>> HP restaurado. Voce esta em Prontera.\n");
}
```

- [ ] **Step 2: Simplify handlePlayerDeath()**

Remove `playerService.ressuscitarJogador()`, `playerRepo.findById()`, `p.setMapName("prontera")`, and `playerRepo.save(p)` — these are now handled by `BattleEventHandler.onPlayerDied()` which fires synchronously before `realizarAtaque()` returns.

New implementation:
```java
private void handlePlayerDeath() {
    inBattle = false;
    currentMonster = null;
    System.out.println("\n>>> VOCE MORREU! Ressuscitando em Prontera...");
    System.out.println(">>> HP restaurado. Voce esta em Prontera.\n");
}
```

**Also check** whether `playerService` and `playerRepo` are used elsewhere in `RagnarokTerminalRunner`. If `playerService.ressuscitarJogador()` was the only call to `playerService`, do NOT remove it — `playerService` is certainly used elsewhere (character creation, etc.). Only remove lines related to the player-death resurrection logic.

- [ ] **Step 3: Run terminal runner tests**

```bash
./mvnw test -Dtest=RagnarokTerminalRunnerTest -q
```

Expected: all tests pass. Remove any mock stubs for `ressuscitarJogador()` or `playerRepo.save()` in `handlePlayerDeath` context if they now cause `UnnecessaryStubbingException`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java
git commit -m "refactor: simplify RagnarokTerminalRunner.handlePlayerDeath() — logic moved to BattleEventHandler"
```

---

## Task 5: Full test suite and final verification

- [ ] **Step 1: Run complete test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`. All 212+ tests pass. JaCoCo ≥ 85% line / ≥ 62% branch.

If any test fails:
- `BattleServiceTest`: check for `UnnecessaryStubbingException` — remove stubs for `levelingService`, `itemMapper`, `playerItemRepository` since `BattleService` no longer uses them directly. Add `@Mock ApplicationEventPublisher` and verify `eventPublisher.publishEvent(any())` is called when monster dies.
- `BattleLootIntegrationTest`: loot is now persisted by `BattleEventHandler`. The test checks the `player_items` table — it should still be populated since the event fires synchronously. If not, verify `BattleEventHandler` is in the Spring context (add `@Import(BattleEventHandler.class)` if the test uses a limited context).

- [ ] **Step 2: Final commit**

```bash
git add .
git commit -m "feat(events): Spring Events complete — BattleService decoupled via domain events"
```
