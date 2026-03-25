package com.ragnarok.api.dto.request;
import java.util.UUID;
public record CreateTradeOfferRequestDTO(Long senderPlayerId, Long receiverPlayerId,
                                          UUID playerItemId, Long requestedZenny) {}
