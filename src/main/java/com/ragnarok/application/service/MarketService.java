package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MarketService {

    private final MarketListingRepository marketListingRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final FraudClient fraudClient;

    public MarketService(MarketListingRepository marketListingRepository,
                         PlayerRepository playerRepository,
                         PlayerItemRepository playerItemRepository,
                         FraudClient fraudClient) {
        this.marketListingRepository = marketListingRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.fraudClient = fraudClient;
    }

    public List<MarketListingEntity> listActive(Long itemId) {
        if (itemId != null) {
            return marketListingRepository.findByItemIdAndStatus(itemId, "ACTIVE");
        }
        return marketListingRepository.findByStatus("ACTIVE");
    }

    @Transactional
    public MarketListingEntity createListing(Long sellerPlayerId, UUID playerItemId,
                                              Long priceZenny, int quantity) {
        PlayerItemEntity pi = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado no inventario"));
        if (pi.getAmount() < quantity) {
            throw new IllegalStateException("Quantidade insuficiente no inventario");
        }

        FraudClient.FraudDecision decision = fraudClient.checkItemTrade(
                sellerPlayerId, pi.getItem().getId(), playerItemId.toString(), priceZenny);
        if (decision.isBlocked()) {
            throw new GameException("Listagem bloqueada pelo sistema antifraude");
        }

        MarketListingEntity listing = new MarketListingEntity();
        listing.setSellerPlayerId(sellerPlayerId);
        listing.setPlayerItemId(playerItemId);
        listing.setItemId(pi.getItem().getId());
        listing.setPriceZenny(priceZenny);
        listing.setQuantity(quantity);
        return marketListingRepository.save(listing);
    }

    @Transactional
    public void buy(Long listingId, Long buyerPlayerId) {
        MarketListingEntity listing = marketListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listagem nao encontrada"));
        if (!"ACTIVE".equals(listing.getStatus())) {
            throw new IllegalStateException("Listagem nao esta mais ativa");
        }
        if (listing.getSellerPlayerId().equals(buyerPlayerId)) {
            throw new IllegalArgumentException("Voce nao pode comprar seu proprio item");
        }

        PlayerEntity seller = playerRepository.findById(listing.getSellerPlayerId()).orElseThrow();
        PlayerEntity buyer = playerRepository.findById(buyerPlayerId).orElseThrow();

        PlayerItemEntity sellerItem = playerItemRepository.findById(listing.getPlayerItemId())
                .orElseThrow(() -> new IllegalStateException("Item nao esta mais no inventario do vendedor"));

        if (buyer.getZenny() < listing.getPriceZenny()) {
            throw new IllegalStateException("Zenny insuficiente");
        }

        long totalActive = marketListingRepository.countByStatus("ACTIVE");
        FraudClient.FraudDecision decision = fraudClient.checkMarketPurchase(
                buyerPlayerId, listing.getItemId(), (long) listing.getQuantity(), totalActive);
        if (decision.isBlocked()) {
            throw new GameException("Compra bloqueada pelo sistema antifraude");
        }

        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(
                buyerPlayerId, listing.getItemId());
        if (!existing.isEmpty()) {
            existing.get(0).setAmount(existing.get(0).getAmount() + listing.getQuantity());
            playerItemRepository.save(existing.get(0));
        } else {
            PlayerItemEntity newPi = new PlayerItemEntity();
            newPi.setPlayer(buyer); newPi.setItem(sellerItem.getItem());
            newPi.setAmount(listing.getQuantity()); newPi.setEquipped(false);
            playerItemRepository.save(newPi);
        }

        if (sellerItem.getAmount() <= listing.getQuantity()) {
            playerItemRepository.delete(sellerItem);
        } else {
            sellerItem.setAmount(sellerItem.getAmount() - listing.getQuantity());
            playerItemRepository.save(sellerItem);
        }

        buyer.setZenny(buyer.getZenny() - listing.getPriceZenny());
        seller.setZenny(seller.getZenny() + listing.getPriceZenny());
        playerRepository.save(buyer);
        playerRepository.save(seller);

        listing.setStatus("SOLD");
        listing.setSoldAt(Instant.now());
        marketListingRepository.save(listing);
    }

    @Transactional
    public void cancel(Long listingId, Long sellerPlayerId) {
        MarketListingEntity listing = marketListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listagem nao encontrada"));
        if (!listing.getSellerPlayerId().equals(sellerPlayerId)) {
            throw new IllegalArgumentException("Apenas o vendedor pode cancelar a listagem");
        }
        listing.setStatus("CANCELLED");
        marketListingRepository.save(listing);
    }
}
