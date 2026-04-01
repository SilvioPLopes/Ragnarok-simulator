package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TradeService {

    private final TradeOfferRepository tradeOfferRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final FraudClient fraudClient;

    public TradeService(TradeOfferRepository tradeOfferRepository,
                        PlayerRepository playerRepository,
                        PlayerItemRepository playerItemRepository,
                        FraudClient fraudClient) {
        this.tradeOfferRepository = tradeOfferRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.fraudClient = fraudClient;
    }

    @Transactional
    public TradeOfferEntity createOffer(Long senderPlayerId, Long receiverPlayerId,
                                        UUID playerItemId, Long requestedZenny) {
        playerRepository.findById(senderPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("Sender nao encontrado"));
        playerRepository.findById(receiverPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("Receiver nao encontrado"));
        playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado"));

        TradeOfferEntity offer = new TradeOfferEntity();
        offer.setSenderPlayerId(senderPlayerId);
        offer.setReceiverPlayerId(receiverPlayerId);
        offer.setOfferedPlayerItemId(playerItemId);
        offer.setRequestedZenny(requestedZenny != null ? requestedZenny : 0L);
        return tradeOfferRepository.save(offer);
    }

    @Transactional
    public void acceptOffer(Long offerId, Long receiverPlayerId) {
        TradeOfferEntity offer = loadPendingOffer(offerId);
        if (!offer.getReceiverPlayerId().equals(receiverPlayerId)) {
            throw new IllegalArgumentException("Voce nao e o destinatario desta oferta");
        }
        PlayerItemEntity item = playerItemRepository.findById(offer.getOfferedPlayerItemId())
                .orElseThrow(() -> new IllegalStateException("Item nao esta mais no inventario do remetente"));

        PlayerEntity sender = playerRepository.findById(offer.getSenderPlayerId()).orElseThrow();
        PlayerEntity receiver = playerRepository.findById(receiverPlayerId).orElseThrow();

        if (receiver.getZenny() < offer.getRequestedZenny()) {
            throw new IllegalStateException("Zenny insuficiente para aceitar a troca");
        }

        FraudClient.FraudDecision decision = fraudClient.checkItemTrade(
                offer.getSenderPlayerId(),
                item.getItem().getId(),
                offer.getOfferedPlayerItemId().toString(),
                offer.getRequestedZenny());
        if (decision.isBlocked()) {
            throw new GameException("Troca bloqueada pelo sistema antifraude");
        }

        item.setPlayer(receiver);
        receiver.setZenny(receiver.getZenny() - offer.getRequestedZenny());
        sender.setZenny(sender.getZenny() + offer.getRequestedZenny());
        offer.setStatus("ACCEPTED");

        playerItemRepository.save(item);
        playerRepository.save(sender);
        playerRepository.save(receiver);
        tradeOfferRepository.save(offer);
    }

    @Transactional
    public void rejectOffer(Long offerId, Long receiverPlayerId) {
        TradeOfferEntity offer = loadPendingOffer(offerId);
        if (!offer.getReceiverPlayerId().equals(receiverPlayerId)) {
            throw new IllegalArgumentException("Voce nao e o destinatario desta oferta");
        }
        offer.setStatus("REJECTED");
        tradeOfferRepository.save(offer);
    }

    @Transactional
    public void cancelOffer(Long offerId, Long senderPlayerId) {
        TradeOfferEntity offer = loadPendingOffer(offerId);
        if (!offer.getSenderPlayerId().equals(senderPlayerId)) {
            throw new IllegalArgumentException("Apenas o remetente pode cancelar a oferta");
        }
        offer.setStatus("CANCELLED");
        tradeOfferRepository.save(offer);
    }

    public List<TradeOfferEntity> listReceived(Long playerId) {
        return tradeOfferRepository.findByReceiverPlayerIdAndStatus(playerId, "PENDING");
    }

    public List<TradeOfferEntity> listSent(Long playerId) {
        return tradeOfferRepository.findBySenderPlayerId(playerId);
    }

    private TradeOfferEntity loadPendingOffer(Long offerId) {
        TradeOfferEntity offer = tradeOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta nao encontrada: " + offerId));
        if (!"PENDING".equals(offer.getStatus())) {
            throw new IllegalStateException("Oferta nao esta mais pendente: " + offer.getStatus());
        }
        return offer;
    }
}
