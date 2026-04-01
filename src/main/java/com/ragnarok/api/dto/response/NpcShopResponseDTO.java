package com.ragnarok.api.dto.response;

import java.util.List;

public record NpcShopResponseDTO(String npcName, List<ShopItemDTO> items) {
    public record ShopItemDTO(Long itemId, String itemName, int price) {}
}
