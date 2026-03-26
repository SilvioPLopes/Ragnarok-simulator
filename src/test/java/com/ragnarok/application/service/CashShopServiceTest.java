package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
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
class CashShopServiceTest {

    @Mock CashShopItemRepository cashShopItemRepository;
    @Mock AccountRepository accountRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock ItemRepository itemRepository;
    @Mock FraudClient fraudClient;

    CashShopService service;

    @BeforeEach
    void setUp() {
        service = new CashShopService(cashShopItemRepository, accountRepository,
                playerRepository, playerItemRepository, itemRepository, fraudClient);
    }

    @Test
    void buy_success() {
        CashShopItemEntity shopItem = cashShopItem(1L, 100L, 500L);
        AccountEntity account = account(10L, 1000L);
        PlayerEntity player = new PlayerEntity(); player.setId(1L);
        ItemEntity item = new ItemEntity(); item.setId(500L);

        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(itemRepository.findById(500L)).thenReturn(Optional.of(item));
        when(cashShopItemRepository.countByActiveTrue()).thenReturn(5L);
        when(fraudClient.checkMarketPurchase(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);
        when(playerItemRepository.findByPlayerIdAndItemId(anyLong(), anyLong())).thenReturn(List.of());

        service.buy(10L, 1L, 1L);

        assertEquals(900L, account.getCashPoints()); // 1000 - 100
    }

    @Test
    void buy_insufficientCashPoints_throws() {
        CashShopItemEntity shopItem = cashShopItem(1L, 500L, 100L);
        AccountEntity account = account(10L, 100L);
        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class, () -> service.buy(10L, 1L, 1L));
    }

    @Test
    void buy_inactiveItem_throws() {
        CashShopItemEntity shopItem = cashShopItem(1L, 100L, 500L);
        shopItem.setActive(false);
        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));

        assertThrows(IllegalArgumentException.class, () -> service.buy(10L, 1L, 1L));
    }

    @Test
    void buy_blockedByFraud_throws() {
        CashShopItemEntity shopItem = cashShopItem(1L, 100L, 500L);
        AccountEntity account = account(10L, 1000L);
        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(cashShopItemRepository.countByActiveTrue()).thenReturn(5L);
        when(fraudClient.checkMarketPurchase(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.BLOCKED);

        assertThrows(GameException.class, () -> service.buy(10L, 1L, 1L));
    }

    private CashShopItemEntity cashShopItem(Long id, Long price, Long itemId) {
        CashShopItemEntity e = new CashShopItemEntity();
        e.setId(id); e.setCashPrice(price); e.setItemId(itemId); e.setActive(true);
        return e;
    }
    private AccountEntity account(Long id, Long cashPoints) {
        AccountEntity a = new AccountEntity();
        a.setId(id); a.setCashPoints(cashPoints);
        return a;
    }
}
