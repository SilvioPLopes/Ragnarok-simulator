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
class TradeServiceTest {

    @Mock TradeOfferRepository tradeOfferRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock FraudClient fraudClient;

    TradeService tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeService(tradeOfferRepository, playerRepository, playerItemRepository, fraudClient);
    }

    @Test
    void createOffer_success() {
        PlayerEntity sender = player(1L, 100L);
        PlayerEntity receiver = player(2L, 50L);
        PlayerItemEntity item = playerItem(sender);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(playerItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(tradeOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> tradeService.createOffer(1L, 2L, item.getId(), 10L));
    }

    @Test
    void acceptOffer_success() {
        UUID itemId = UUID.randomUUID();
        TradeOfferEntity offer = offer(1L, 2L, itemId, 20L);
        PlayerEntity sender = player(1L, 100L);
        PlayerEntity receiver = player(2L, 50L);
        PlayerItemEntity item = playerItem(itemId, sender, 1001L);

        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(playerItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(fraudClient.checkItemTrade(anyLong(), anyLong(), anyString(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);

        tradeService.acceptOffer(offer.getId(), 2L);

        assertEquals(receiver, item.getPlayer());
        assertEquals(30L, receiver.getZenny()); // 50 - 20
        assertEquals("ACCEPTED", offer.getStatus());
    }

    @Test
    void acceptOffer_senderLostItem_throws() {
        UUID itemId = UUID.randomUUID();
        TradeOfferEntity offer = offer(1L, 2L, itemId, 0L);
        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(playerItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> tradeService.acceptOffer(offer.getId(), 2L));
    }

    @Test
    void acceptOffer_insufficientZenny_throws() {
        UUID itemId = UUID.randomUUID();
        TradeOfferEntity offer = offer(1L, 2L, itemId, 100L);
        PlayerEntity sender = player(1L, 500L);
        PlayerEntity receiver = player(2L, 50L);
        PlayerItemEntity item = playerItem(itemId, sender, 1001L);

        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(playerItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class, () -> tradeService.acceptOffer(offer.getId(), 2L));
    }

    @Test
    void rejectOffer_success() {
        TradeOfferEntity offer = offer(1L, 2L, UUID.randomUUID(), 0L);
        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        tradeService.rejectOffer(offer.getId(), 2L);

        assertEquals("REJECTED", offer.getStatus());
    }

    @Test
    void cancelOffer_byNonSender_throws() {
        TradeOfferEntity offer = offer(1L, 2L, UUID.randomUUID(), 0L);
        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThrows(IllegalArgumentException.class, () -> tradeService.cancelOffer(offer.getId(), 99L));
    }

    // helpers
    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity(); p.setId(id); p.setZenny(zenny); return p;
    }
    private PlayerItemEntity playerItem(PlayerEntity owner) {
        return playerItem(UUID.randomUUID(), owner, 1001L);
    }
    private PlayerItemEntity playerItem(UUID id, PlayerEntity owner, Long itemId) {
        PlayerItemEntity pi = new PlayerItemEntity(); pi.setId(id); pi.setPlayer(owner);
        ItemEntity item = new ItemEntity(); item.setId(itemId); pi.setItem(item); pi.setAmount(1);
        return pi;
    }
    private TradeOfferEntity offer(Long senderId, Long receiverId, UUID itemId, Long zenny) {
        TradeOfferEntity o = new TradeOfferEntity();
        o.setId(1L); o.setSenderPlayerId(senderId); o.setReceiverPlayerId(receiverId);
        o.setOfferedPlayerItemId(itemId); o.setRequestedZenny(zenny); o.setStatus("PENDING");
        return o;
    }
}
