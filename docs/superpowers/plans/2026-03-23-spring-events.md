# Spring Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple `BattleService` from loot persistence, XP processing, and player death handling by publishing domain events. New consequences of battles can be added by implementing a new `@EventListener` without touching `BattleService`.

**Architecture:** Three immutable domain event records in `com.ragnarok.domain.event`. `BattleService` publishes these events via `ApplicationEventPublisher`. A new `BattleEventHandler` in the application layer holds all `@EventListener` methods and delegates to existing services. `LevelingService` stays pure domain (no Spring annotations). `RagnarokTerminalRunner.handlePlayerDeath()` is simplified — the resurrection and map-reset logic moves to `BattleEventHandler`.

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

- [ ] **Step 1: Create event records**

`src/main/java/com/ragnarok/domain/event/MonsterKilledEvent.java`:
```java
package com.ragnarok.domain.event;

import com.ragnarok.domain.model.Monster;

/**
 * Published when a player defeats a monster.
 * Carries all data needed for listeners to process loot and XP — no repository access needed.
 */
public record MonsterKilledEvent(
        Long playerId,
        Monster monster
) {}
```

`src/main/java/com/ragnarok/domain/event/PlayerLeveledUpEvent.java`:
```java
package com.ragnarok.domain.event;

/**
 * Published when a player gains a base or job level.
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
- Handles `MonsterKilledEvent` → persists loot drops + processes XP/level via `LevelingService`
- Handles `PlayerDiedEvent` → resurrects player + resets map to prontera

This class replicates the logic currently in `BattleService.processarMorteMonstro()` and `RagnarokTerminalRunner.handlePlayerDeath()`.

**IMPORTANT:** Spring's synchronous `ApplicationEventPublisher` fires listeners in the same thread and transaction as the publisher. This means loot and XP are persisted atomically within `BattleService.realizarAtaque()`'s `@Transactional` boundary — same behavior as before.

- [ ] **Step 1: Read BattleService.processarMorteMonstro() and RagnarokTerminalRunner.handlePlayerDeath()**

Read `src/main/java/com/ragnarok/application/service/BattleService.java` lines 120–188 and `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java` lines 658–667 before writing this class.

- [ ] **Step 2: Write failing test**

`src/test/java/com/ragnarok/application/service/BattleEventHandlerTest.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.model.Monster;
import com.ragnarok.domain.model.MonsterDrop;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleEventHandlerTest {

    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock MonsterRepository monsterRepository;
    @Mock ItemMapper itemMapper;
    @Mock PlayerMapper playerMapper;
    @Mock LevelingService levelingService;

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

        Monster monster = new Monster();
        monster.setName("Poring");
        monster.setBaseExp(40L);
        monster.setJobExp(20L);
        monster.setDrops(List.of());

        handler.onMonsterKilled(new MonsterKilledEvent(1L, monster));

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
        assert playerEntity.getHpCurrent().equals(200);
        assert "prontera".equals(playerEntity.getMapName());
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
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.model.Monster;
import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public BattleEventHandler(PlayerRepository playerRepository,
                               PlayerItemRepository playerItemRepository,
                               ItemMapper itemMapper,
                               PlayerMapper playerMapper,
                               LevelingService levelingService) {
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemMapper = itemMapper;
        this.playerMapper = playerMapper;
        this.levelingService = levelingService;
    }

    /**
     * Handles monster death: persists loot drops and processes XP/level up.
     */
    @EventListener
    public void onMonsterKilled(MonsterKilledEvent event) {
        PlayerEntity playerEntity = playerRepository.findById(event.playerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + event.playerId()));

        Monster monster = event.monster();

        // 1. Persist loot drops (logic moved from BattleService.processarMorteMonstro)
        List<Item> loots = new BattleEngine().calculateLoot(monster);
        for (Item itemDomain : loots) {
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
        long baseExpGain = monster.getBaseExp() != null ? monster.getBaseExp() : 0;
        long jobExpGain = monster.getJobExp() != null ? monster.getJobExp() : 0;

        Player playerDomain = playerMapper.toDomain(playerEntity);
        String levelLog = levelingService.processarExperiencia(playerDomain, baseExpGain, jobExpGain);
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

Expected: all pass. This is your baseline — if they pass after the refactor, the behavior is preserved.

- [ ] **Step 3: Refactor BattleService**

Changes:
1. Add `ApplicationEventPublisher` field and constructor parameter
2. Replace `processarMorteMonstro()` call with `eventPublisher.publishEvent(new MonsterKilledEvent(...))`
3. When player HP reaches 0 after counter-attack: publish `PlayerDiedEvent`
4. Remove `processarMorteMonstro()` method (logic is now in `BattleEventHandler`)
5. Remove unused imports: `LevelingService`, `ItemMapper`, `MonsterMapper`, `PlayerItemRepository` — **only if they are solely used by `processarMorteMonstro()`**. Check all usages before removing.

Updated `BattleService.java`:
```java
package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    private final PlayerRepository playerRepository;
    private final MonsterRepository monsterRepository;
    private final PlayerMapper playerMapper;
    private final PlayerItemRepository playerItemRepository;
    private final ItemMapper itemMapper;
    private final MonsterMapper monsterMapper;
    private final BattleEngine battleEngine;
    private final LevelingService levelingService;
    private final WeaponSizeService weaponSizeService;
    private final ApplicationEventPublisher eventPublisher;

    public BattleService(PlayerRepository playerRepository,
                         MonsterRepository monsterRepository,
                         PlayerItemRepository playerItemRepository,
                         PlayerMapper playerMapper,
                         ItemMapper itemMapper,
                         MonsterMapper monsterMapper,
                         BattleEngine battleEngine,
                         LevelingService levelingService,
                         WeaponSizeService weaponSizeService,
                         ApplicationEventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.monsterRepository = monsterRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemMapper = itemMapper;
        this.playerMapper = playerMapper;
        this.monsterMapper = monsterMapper;
        this.battleEngine = battleEngine;
        this.levelingService = levelingService;
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
            // Publish event — BattleEventHandler handles loot and XP (synchronous, same transaction)
            eventPublisher.publishEvent(new MonsterKilledEvent(playerId, monster));
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

**Note on test compatibility:** `BattleServiceTest` mocks `LevelingService`. After this refactor, `BattleService` still has `LevelingService` in its constructor (kept for backward compatibility and because `BattleEventHandler` uses it indirectly). The mock setup in `BattleServiceTest` may need to be updated — `levelingService.processarExperiencia()` is no longer called directly by `BattleService`. Review `BattleServiceTest` and remove stubs for `levelingService` if they cause `UnnecessaryStubbingException`.

**Note on VITÓRIA message:** The existing terminal runner checks `resultado.contains("VITORIA") || resultado.contains("VITÓRIA")`. The new return value `"\uD83C\uDF1F VITÓRIA! ..."` contains "VITÓRIA" — the check still works. The loot/XP details are now in the log instead of the return string. Update this string if the terminal behavior needs preserving exactly.

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
- `BattleServiceTest` may throw `UnnecessaryStubbingException` if `levelingService` stubs are no longer needed. Remove them.
- `BattleIntegrationTest` checks `resultado.contains("causou 340 de dano")` — this still works since the damage string is preserved.
- `BattleLootIntegrationTest` checks that drops are persisted — they now go through `BattleEventHandler`. This test should still pass since the event fires synchronously in the same transaction.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/BattleService.java
git commit -m "feat(events): BattleService publishes MonsterKilledEvent and PlayerDiedEvent"
```

---

## Task 4: Simplify RagnarokTerminalRunner.handlePlayerDeath()

**Files:**
- Modify: `src/main/java/com/ragnarok/runner/RagnarokTerminalRunner.java`

The resurrection and map reset logic is now handled by `BattleEventHandler.onPlayerDied()`. The terminal runner only needs to update UI state.

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

**Also remove** the now-unused `playerService` import/field if `playerService` is no longer used anywhere else in `RagnarokTerminalRunner`. Check all usages of `playerService` first — if it's used elsewhere (e.g., `ressuscitarJogador` in another path), keep it.

- [ ] **Step 3: Run terminal runner tests**

```bash
./mvnw test -Dtest=RagnarokTerminalRunnerTest -q
```

Expected: all tests pass. The terminal runner test mocks `playerService` — if `ressuscitarJogador()` is no longer called, verify the mock setup doesn't throw `UnnecessaryStubbingException`.

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
- `BattleServiceTest`: check for `UnnecessaryStubbingException` — remove stubs for `levelingService.processarExperiencia()` since `BattleService` no longer calls it directly
- `BattleLootIntegrationTest`: loot is now persisted by `BattleEventHandler`. The test checks the `player_items` table — it should still be populated since the event fires synchronously. If not, add `@Transactional` to the test or flush the entity manager after the attack

- [ ] **Step 2: Verify event flow with integration test**

```bash
./mvnw test -Dtest=BattleIntegrationTest -q
```

The result string no longer includes detailed loot/XP info (it's now logged, not returned). Verify `BattleIntegrationTest` assertions still hold — if they check for loot strings in the result, update them to check the database state instead.

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat(events): Spring Events complete — BattleService decoupled via domain events"
```
