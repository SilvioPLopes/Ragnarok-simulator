package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.ShopItemResponseDTO;
import com.ragnarok.application.service.NpcShopService;
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
    public void sell(@RequestBody NpcSellRequestDTO dto) {
        npcShopService.sell(dto.playerId(), dto.playerItemId(), dto.quantity());
    }
}
