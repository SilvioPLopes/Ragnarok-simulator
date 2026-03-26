package com.ragnarok.api.dto.request;
import java.util.UUID;
public record CreateListingRequestDTO(Long sellerPlayerId, UUID playerItemId,
                                       Long priceZenny, Integer quantity) {}
