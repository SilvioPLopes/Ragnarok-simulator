package com.ragnarok.api.dto.response;

public record InventoryItemResponseDTO(
        String id, String name, String type,
        Integer amount, Boolean equipped,
        String imgUrl,
        String description
) {}
