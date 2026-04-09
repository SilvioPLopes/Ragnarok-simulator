package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

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
        log.info("[Market] Criando listagem: vendedor={}, playerItemId={}, preco={}, qty={}", sellerPlayerId, playerItemId, priceZenny, quantity);
        PlayerItemEntity pi = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado no inventario"));
        if (pi.getAmount() < quantity) {
            log.warn("[Market] Quantidade insuficiente para listagem: playerId={}, disponivel={}, pedido={}", sellerPlayerId, pi.getAmount(), quantity);
            throw new IllegalStateException("Quantidade insuficiente no inventario");
        }

        FraudClient.FraudDecision decision = fraudClient.checkItemTrade(
                sellerPlayerId, pi.getItem().getId(), playerItemId.toString(), priceZenny);
        if (decision.isBlocked()) {
            log.warn("[Market] Listagem bloqueada por antifraude: vendedor={}, itemId={}", sellerPlayerId, pi.getItem().getId());
            throw new GameException("Listagem bloqueada pelo sistema antifraude");
        }

        MarketListingEntity listing = new MarketListingEntity();
        listing.setSellerPlayerId(sellerPlayerId);
        listing.setPlayerItemId(playerItemId);
        listing.setItemId(pi.getItem().getId());
        listing.setPriceZenny(priceZenny);
        listing.setQuantity(quantity);
        MarketListingEntity saved = marketListingRepository.save(listing);
        log.info("[Market] Listagem criada: listingId={}, itemId={}", saved.getId(), pi.getItem().getId());
        return saved;
    }

    @Transactional
    public void buy(Long listingId, Long buyerPlayerId) {
        log.info("[Market] Compra iniciada: listingId={}, comprador={}", listingId, buyerPlayerId);
        MarketListingEntity listing = marketListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listagem nao encontrada"));
        if (!"ACTIVE".equals(listing.getStatus())) {
            log.warn("[Market] Tentativa de comprar listagem inativa: listingId={}, status={}", listingId, listing.getStatus());
            throw new IllegalStateException("Listagem nao esta mais ativa");
        }
        if (listing.getSellerPlayerId().equals(buyerPlayerId)) {
            throw new IllegalArgumentException("Voce nao pode comprar seu proprio item");
        }

        PlayerEntity seller = playerRepository.findById(listing.getSellerPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("Vendedor nao encontrado: " + listing.getSellerPlayerId()));
        PlayerEntity buyer = playerRepository.findById(buyerPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("Comprador nao encontrado: " + buyerPlayerId));

        PlayerItemEntity sellerItem = playerItemRepository.findById(listing.getPlayerItemId())
                .orElseThrow(() -> new IllegalStateException("Item nao esta mais no inventario do vendedor"));

        if (buyer.getZenny() < listing.getPriceZenny()) {
            log.warn("[Market] Zenny insuficiente: comprador={}, necessario={}, disponivel={}", buyerPlayerId, listing.getPriceZenny(), buyer.getZenny());
            throw new IllegalStateException("Zenny insuficiente");
        }

        long totalActive = marketListingRepository.countByStatus("ACTIVE");
        FraudClient.FraudDecision decision = fraudClient.checkMarketPurchase(
                buyerPlayerId, listing.getItemId(), (long) listing.getQuantity(), totalActive);
        if (decision.isBlocked()) {
            log.warn("[Market] Compra bloqueada por antifraude: comprador={}, listingId={}", buyerPlayerId, listingId);
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
        log.info("[Market] Compra concluida: listingId={}, comprador={}, vendedor={}, zenny={}", listingId, buyerPlayerId, listing.getSellerPlayerId(), listing.getPriceZenny());
    }

    @Transactional
    public void cancel(Long listingId, Long sellerPlayerId) {
        log.info("[Market] Cancelamento de listagem: listingId={}, vendedor={}", listingId, sellerPlayerId);
        MarketListingEntity listing = marketListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listagem nao encontrada"));
        if (!listing.getSellerPlayerId().equals(sellerPlayerId)) {
            log.warn("[Market] Tentativa de cancelar listagem de outro vendedor: solicitante={}, dono={}", sellerPlayerId, listing.getSellerPlayerId());
            throw new IllegalArgumentException("Apenas o vendedor pode cancelar a listagem");
        }
        listing.setStatus("CANCELLED");
        marketListingRepository.save(listing);
        log.info("[Market] Listagem cancelada: listingId={}", listingId);
    }
}
