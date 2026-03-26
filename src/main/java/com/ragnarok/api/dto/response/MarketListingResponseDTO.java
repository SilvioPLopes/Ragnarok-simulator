package com.ragnarok.api.dto.response;
import com.ragnarok.infrastructure.persistence.MarketListingEntity;
import java.util.UUID;
public record MarketListingResponseDTO(Long id, Long sellerPlayerId, UUID playerItemId,
                                        Long itemId, Long priceZenny, Integer quantity, String status) {
    public static MarketListingResponseDTO from(MarketListingEntity e) {
        return new MarketListingResponseDTO(e.getId(), e.getSellerPlayerId(), e.getPlayerItemId(),
                e.getItemId(), e.getPriceZenny(), e.getQuantity(), e.getStatus());
    }
}
