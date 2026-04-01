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
class MarketServiceTest {

    @Mock MarketListingRepository marketListingRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock FraudClient fraudClient;

    MarketService service;

    @BeforeEach
    void setUp() {
        service = new MarketService(marketListingRepository, playerRepository, playerItemRepository, fraudClient);
    }

    @Test
    void createListing_success() {
        UUID piId = UUID.randomUUID();
        PlayerItemEntity pi = playerItem(piId, player(1L, 0L), 100L, 5);
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));
        when(fraudClient.checkItemTrade(anyLong(), anyLong(), anyString(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);
        when(marketListingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.createListing(1L, piId, 200L, 3));
        verify(marketListingRepository).save(any());
    }

    @Test
    void createListing_insufficientQuantity_throws() {
        UUID piId = UUID.randomUUID();
        PlayerItemEntity pi = playerItem(piId, player(1L, 0L), 100L, 2);
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));

        assertThrows(IllegalStateException.class, () -> service.createListing(1L, piId, 100L, 5));
    }

    @Test
    void buy_success() {
        UUID piId = UUID.randomUUID();
        PlayerEntity seller = player(1L, 0L);
        PlayerEntity buyer = player(2L, 500L);
        PlayerItemEntity pi = playerItem(piId, seller, 100L, 3);
        MarketListingEntity listing = listing(1L, 1L, piId, 100L, 200L, 2);

        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(buyer));
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));
        when(marketListingRepository.countByStatus("ACTIVE")).thenReturn(10L);
        when(fraudClient.checkMarketPurchase(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);
        when(playerItemRepository.findByPlayerIdAndItemId(anyLong(), anyLong())).thenReturn(List.of());

        service.buy(1L, 2L);

        assertEquals(300L, buyer.getZenny());
        assertEquals(200L, seller.getZenny());
        assertEquals("SOLD", listing.getStatus());
    }

    @Test
    void buy_selfPurchase_throws() {
        MarketListingEntity listing = listing(1L, 1L, UUID.randomUUID(), 100L, 200L, 1);
        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThrows(IllegalArgumentException.class, () -> service.buy(1L, 1L));
    }

    @Test
    void buy_insufficientZenny_throws() {
        UUID piId = UUID.randomUUID();
        PlayerEntity seller = player(1L, 0L);
        PlayerEntity buyer = player(2L, 50L);
        PlayerItemEntity pi = playerItem(piId, seller, 100L, 2);
        MarketListingEntity listing = listing(1L, 1L, piId, 100L, 200L, 2);

        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(buyer));
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));

        assertThrows(IllegalStateException.class, () -> service.buy(1L, 2L));
    }

    @Test
    void cancel_success() {
        MarketListingEntity listing = listing(1L, 1L, UUID.randomUUID(), 100L, 200L, 1);
        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));

        service.cancel(1L, 1L);

        assertEquals("CANCELLED", listing.getStatus());
    }

    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity(); p.setId(id); p.setZenny(zenny); return p;
    }
    private PlayerItemEntity playerItem(UUID id, PlayerEntity owner, Long itemId, int amount) {
        PlayerItemEntity pi = new PlayerItemEntity(); pi.setId(id); pi.setPlayer(owner);
        ItemEntity item = new ItemEntity(); item.setId(itemId); pi.setItem(item); pi.setAmount(amount);
        return pi;
    }
    private MarketListingEntity listing(Long id, Long sellerId, UUID piId, Long itemId, Long price, int qty) {
        MarketListingEntity l = new MarketListingEntity();
        l.setId(id); l.setSellerPlayerId(sellerId); l.setPlayerItemId(piId);
        l.setItemId(itemId); l.setPriceZenny(price); l.setQuantity(qty); l.setStatus("ACTIVE");
        return l;
    }
}
