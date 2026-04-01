package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NpcShopServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;

    NpcShopService service;

    @BeforeEach
    void setUp() {
        service = new NpcShopService(itemRepository, playerRepository, playerItemRepository);
    }

    @Test
    void buy_success() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 500L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(playerItemRepository.findByPlayerIdAndItemId(1L, 1L)).thenReturn(List.of());

        service.buy(1L, 1L, 2);

        assertEquals(300L, player.getZenny());
        verify(playerItemRepository).save(any());
    }

    @Test
    void buy_insufficientZenny_throws() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 50L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        assertThrows(IllegalStateException.class, () -> service.buy(1L, 1L, 1));
    }

    @Test
    void sell_success() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 0L);
        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setId(UUID.randomUUID()); pi.setPlayer(player); pi.setItem(item); pi.setAmount(3);
        when(playerItemRepository.findById(pi.getId())).thenReturn(Optional.of(pi));

        service.sell(1L, pi.getId(), 2);

        assertEquals(100L, player.getZenny()); // 100 * 2 * 0.5
        assertEquals(1, pi.getAmount());
    }

    @Test
    void sell_moreThanOwned_throws() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 0L);
        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setId(UUID.randomUUID()); pi.setPlayer(player); pi.setItem(item); pi.setAmount(1);
        when(playerItemRepository.findById(pi.getId())).thenReturn(Optional.of(pi));

        assertThrows(IllegalStateException.class, () -> service.sell(1L, pi.getId(), 5));
    }

    private ItemEntity item(Long id, String name, int price) {
        ItemEntity e = new ItemEntity(); e.setId(id); e.setName(name); e.setPrice(price); return e;
    }
    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity(); p.setId(id); p.setZenny(zenny); return p;
    }
}
