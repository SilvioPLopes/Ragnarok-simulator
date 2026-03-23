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

    @EventListener
    public void onMonsterKilled(MonsterKilledEvent event) {
        PlayerEntity playerEntity = playerRepository.findById(event.playerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + event.playerId()));

        // Persist loot
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

        // Process XP
        int baseLevelBefore = playerEntity.getBaseLevel() != null ? playerEntity.getBaseLevel() : 1;
        int jobLevelBefore  = playerEntity.getJobLevel()  != null ? playerEntity.getJobLevel()  : 1;

        Player playerDomain = playerMapper.toDomain(playerEntity);
        String levelLog = levelingService.processarExperiencia(playerDomain, event.baseExp(), event.jobExp());
        log.info("XP processed for player {}: {}", event.playerId(), levelLog);

        // Persist updated stats (null-safe: keep existing value if domain field is null)
        if (playerDomain.getBaseLevel()  != null) playerEntity.setBaseLevel(playerDomain.getBaseLevel());
        if (playerDomain.getJobLevel()   != null) playerEntity.setJobLevel(playerDomain.getJobLevel());
        if (playerDomain.getBaseExp()    != null) playerEntity.setBaseExp(playerDomain.getBaseExp());
        if (playerDomain.getJobExp()     != null) playerEntity.setJobExp(playerDomain.getJobExp());
        if (playerDomain.getStatPoints() != null) playerEntity.setStatPoints(playerDomain.getStatPoints());
        if (playerDomain.getSkillPoints()!= null) playerEntity.setSkillPoints(playerDomain.getSkillPoints());
        if (playerDomain.getHpCurrent()  != null) playerEntity.setHpCurrent(playerDomain.getHpCurrent());
        if (playerDomain.getSpCurrent()  != null) playerEntity.setSpCurrent(playerDomain.getSpCurrent());
        playerRepository.save(playerEntity);

        // Publish level up event if leveled
        int baseLevelAfter = playerDomain.getBaseLevel() != null ? playerDomain.getBaseLevel() : 1;
        int jobLevelAfter  = playerDomain.getJobLevel()  != null ? playerDomain.getJobLevel()  : 1;
        boolean leveled = baseLevelAfter > baseLevelBefore
                       || jobLevelAfter  > jobLevelBefore;
        if (leveled) {
            log.info("Player {} leveled up! Base={}, Job={}.",
                    event.playerId(), baseLevelAfter, jobLevelAfter);
            eventPublisher.publishEvent(new PlayerLeveledUpEvent(
                    event.playerId(), baseLevelAfter, jobLevelAfter));
        }
    }

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
