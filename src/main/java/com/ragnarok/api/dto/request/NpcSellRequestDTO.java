package com.ragnarok.api.dto.request;
import java.util.UUID;
public record NpcSellRequestDTO(Long playerId, UUID playerItemId, Integer quantity) {}
