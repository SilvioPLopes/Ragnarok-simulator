package com.ragnarok.application.service;

import com.ragnarok.api.dto.response.NpcBuyResponseDTO;
import com.ragnarok.api.dto.response.NpcShopResponseDTO;
import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NpcServiceTest {

    @Mock NpcRepository npcRepository;
    @Mock NpcShopItemRepository shopItemRepository;
    @Mock NpcWarpDestinationRepository warpDestinationRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock ItemRepository itemRepository;

    NpcService service;

    @BeforeEach
    void setUp() {
        service = new NpcService(npcRepository, shopItemRepository, warpDestinationRepository,
                playerRepository, playerItemRepository, itemRepository);
    }

    @Test
    void getShop_returnsRealItemNameFromItemsTable_notDummyFromShopTable() {
        NpcEntity npc = shopNpc(1L, "Prontera Shop");
        NpcShopItemEntity shopItem = shopItem(101L, "Item 519", 50); // dummy name from npc_shop_items
        ItemEntity realItem = item(101L, "Red Potion", 50);          // real name from items table

        when(npcRepository.findById(1L)).thenReturn(Optional.of(npc));
        when(shopItemRepository.findByNpcId(1L)).thenReturn(List.of(shopItem));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(realItem));

        NpcShopResponseDTO result = service.getShop(1L);

        assertEquals("Red Potion", result.items().get(0).itemName());
    }

    @Test
    void buyFromNpc_returnsRealItemNameFromItemsTable_notDummyFromShopTable() {
        NpcEntity npc = shopNpc(1L, "Prontera Shop");
        NpcShopItemEntity shopItem = shopItem(101L, "Item 519", 100); // dummy name
        ItemEntity realItem = item(101L, "Red Potion", 100);
        PlayerEntity player = player(10L, 500L);

        when(npcRepository.findById(1L)).thenReturn(Optional.of(npc));
        when(shopItemRepository.findByNpcIdAndItemId(1L, 101L)).thenReturn(Optional.of(shopItem));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(realItem));
        when(playerItemRepository.findByPlayerIdAndItemId(10L, 101L)).thenReturn(List.of());

        NpcBuyResponseDTO result = service.buyFromNpc(1L, 10L, 101L, 1);

        assertEquals("Red Potion", result.itemName());
    }

    // --- helpers ---

    private NpcEntity shopNpc(Long id, String name) {
        NpcEntity e = new NpcEntity();
        e.setId(id);
        e.setName(name);
        e.setType(NpcType.SHOP);
        return e;
    }

    private NpcShopItemEntity shopItem(Long itemId, String dummyName, int price) {
        NpcShopItemEntity e = new NpcShopItemEntity();
        e.setItemId(itemId);
        e.setItemName(dummyName);
        e.setPrice(price);
        return e;
    }

    private ItemEntity item(Long id, String name, int price) {
        ItemEntity e = new ItemEntity();
        e.setId(id);
        e.setName(name);
        e.setPrice(price);
        return e;
    }

    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity();
        p.setId(id);
        p.setZenny(zenny);
        return p;
    }
}
