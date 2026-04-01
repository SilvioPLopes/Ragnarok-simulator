package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.InventoryItemResponseDTO;
import com.ragnarok.api.dto.response.ShopItemResponseDTO;
import com.ragnarok.application.service.NpcShopService;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/shop/npc")
public class NpcShopController {

    private final NpcShopService npcShopService;

    public NpcShopController(NpcShopService npcShopService) {
        this.npcShopService = npcShopService;
    }

    @GetMapping("/items")
    public List<ShopItemResponseDTO> list() {
        return npcShopService.listItems().stream().map(ShopItemResponseDTO::fromItem).toList();
    }

    @PostMapping("/buy")
    public void buy(@RequestBody NpcBuyRequestDTO dto) {
        npcShopService.buy(dto.playerId(), dto.itemId(), dto.quantity());
    }

    @PostMapping("/sell")
    public List<InventoryItemResponseDTO> sell(@RequestBody NpcSellRequestDTO dto) {
        return npcShopService.sell(dto.playerId(), dto.playerItemId(), dto.quantity())
                .stream().map(this::toDTO).toList();
    }

    private InventoryItemResponseDTO toDTO(PlayerItemEntity pi) {
        return new InventoryItemResponseDTO(
                pi.getId().toString(),
                pi.getItem() != null ? pi.getItem().getName() : "Unknown",
                pi.getItem() != null && pi.getItem().getType() != null
                        ? pi.getItem().getType().name() : "UNKNOWN",
                pi.getAmount(),
                pi.getEquipped());
    }
}
