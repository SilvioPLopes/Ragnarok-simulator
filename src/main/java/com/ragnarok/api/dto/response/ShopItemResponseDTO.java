package com.ragnarok.api.dto.response;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.CashShopItemEntity;
public record ShopItemResponseDTO(Long id, String name, Integer price, Long cashPrice) {
    public static ShopItemResponseDTO fromItem(ItemEntity e) {
        return new ShopItemResponseDTO(e.getId(), e.getName(), e.getPrice(), null);
    }
    public static ShopItemResponseDTO fromCashShopItem(CashShopItemEntity e, String itemName) {
        return new ShopItemResponseDTO(e.getId(), itemName, null, e.getCashPrice());
    }
}
