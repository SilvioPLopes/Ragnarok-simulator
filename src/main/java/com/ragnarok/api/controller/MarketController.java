package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.MarketListingResponseDTO;
import com.ragnarok.application.service.MarketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market/listings")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    public List<MarketListingResponseDTO> list(@RequestParam(required = false) Long itemId) {
        return marketService.listActive(itemId).stream().map(MarketListingResponseDTO::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarketListingResponseDTO create(@RequestBody CreateListingRequestDTO dto) {
        return MarketListingResponseDTO.from(
                marketService.createListing(dto.sellerPlayerId(), dto.playerItemId(),
                        dto.priceZenny(), dto.quantity()));
    }

    @PostMapping("/{id}/buy")
    public void buy(@PathVariable Long id, @RequestBody BuyListingRequestDTO dto) {
        marketService.buy(id, dto.buyerPlayerId());
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, @RequestBody BuyListingRequestDTO dto) {
        marketService.cancel(id, dto.buyerPlayerId());
    }
}
