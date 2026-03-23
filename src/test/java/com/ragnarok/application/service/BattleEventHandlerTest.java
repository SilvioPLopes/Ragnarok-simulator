package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.event.PlayerDiedEvent;
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.service.LevelingService;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.ItemEntity;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleEventHandlerTest {

    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
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

    @Test
    void onMonsterKilled_withLoot_persistsNewInventoryItem() {
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setId(1L);
        playerEntity.setHpMax(100);
        playerEntity.setHpCurrent(80);

        Item item = new Item();
        item.setId(10L);

        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setId(10L);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerEntity));
        when(playerMapper.toDomain(playerEntity)).thenReturn(new com.ragnarok.domain.model.Player());
        when(levelingService.processarExperiencia(any(), anyLong(), anyLong())).thenReturn("Sem level up.");
        when(itemMapper.toEntity(item)).thenReturn(itemEntity);
        when(playerItemRepository.findByPlayerIdAndItemId(1L, 10L)).thenReturn(List.of());

        handler.onMonsterKilled(new MonsterKilledEvent(1L, 999L, List.of(item), 40L, 20L));

        verify(playerItemRepository).save(any(PlayerItemEntity.class));
    }
}
