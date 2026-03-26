package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.CashBuyRequestDTO;
import com.ragnarok.api.dto.response.ShopItemResponseDTO;
import com.ragnarok.application.service.CashShopService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/shop/cash")
public class CashShopController {

    private final CashShopService cashShopService;

    public CashShopController(CashShopService cashShopService) {
        this.cashShopService = cashShopService;
    }

    @GetMapping("/items")
    public List<ShopItemResponseDTO> list() {
        return cashShopService.listItems().stream()
                .map(e -> ShopItemResponseDTO.fromCashShopItem(e, "Item #" + e.getItemId()))
                .toList();
    }

    @PostMapping("/buy")
    public void buy(@RequestBody CashBuyRequestDTO dto) {
        cashShopService.buy(dto.accountId(), dto.cashShopItemId(), dto.playerId());
    }
}
