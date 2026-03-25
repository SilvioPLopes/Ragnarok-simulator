package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.TradeOfferResponseDTO;
import com.ragnarok.application.service.TradeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trade/offers")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradeOfferResponseDTO create(@RequestBody CreateTradeOfferRequestDTO dto) {
        return TradeOfferResponseDTO.from(
                tradeService.createOffer(dto.senderPlayerId(), dto.receiverPlayerId(),
                        dto.playerItemId(), dto.requestedZenny()));
    }

    @GetMapping("/received/{playerId}")
    public List<TradeOfferResponseDTO> listReceived(@PathVariable Long playerId) {
        return tradeService.listReceived(playerId).stream().map(TradeOfferResponseDTO::from).toList();
    }

    @GetMapping("/sent/{playerId}")
    public List<TradeOfferResponseDTO> listSent(@PathVariable Long playerId) {
        return tradeService.listSent(playerId).stream().map(TradeOfferResponseDTO::from).toList();
    }

    @PostMapping("/{id}/accept")
    public void accept(@PathVariable Long id, @RequestBody TradeActionRequestDTO dto) {
        tradeService.acceptOffer(id, dto.playerId());
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id, @RequestBody TradeActionRequestDTO dto) {
        tradeService.rejectOffer(id, dto.playerId());
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, @RequestBody TradeActionRequestDTO dto) {
        tradeService.cancelOffer(id, dto.playerId());
    }
}
