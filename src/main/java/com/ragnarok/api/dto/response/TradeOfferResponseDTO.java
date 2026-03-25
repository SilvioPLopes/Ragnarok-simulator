package com.ragnarok.api.dto.response;
import com.ragnarok.infrastructure.persistence.TradeOfferEntity;
import java.util.UUID;
public record TradeOfferResponseDTO(Long id, Long senderPlayerId, Long receiverPlayerId,
                                     UUID offeredPlayerItemId, Long requestedZenny, String status) {
    public static TradeOfferResponseDTO from(TradeOfferEntity e) {
        return new TradeOfferResponseDTO(e.getId(), e.getSenderPlayerId(), e.getReceiverPlayerId(),
                e.getOfferedPlayerItemId(), e.getRequestedZenny(), e.getStatus());
    }
}
