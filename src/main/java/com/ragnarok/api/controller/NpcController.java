package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.*;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.NpcService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NpcController {

    private final NpcService npcService;
    private final AccountService accountService;

    public NpcController(NpcService npcService, AccountService accountService) {
        this.npcService = npcService;
        this.accountService = accountService;
    }

    @GetMapping("/maps/{mapName}/npcs")
    public List<NpcResponseDTO> getNpcsForMap(@PathVariable String mapName) {
        return npcService.getNpcsForMap(mapName);
    }

    @GetMapping("/npcs/{npcId}/shop")
    public NpcShopResponseDTO getShop(@PathVariable Long npcId) {
        return npcService.getShop(npcId);
    }

    @PostMapping("/npcs/{npcId}/buy")
    public NpcBuyResponseDTO buyFromNpc(@PathVariable Long npcId,
                                        @RequestBody NpcBuyFromNpcRequestDTO dto,
                                        HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        accountService.validateOwnership(accountId, dto.playerId());
        return npcService.buyFromNpc(npcId, dto.playerId(), dto.itemId(), dto.amount());
    }

    @PostMapping("/npcs/{npcId}/heal")
    public NpcHealResponseDTO heal(@PathVariable Long npcId,
                                   @RequestBody NpcHealRequestDTO dto,
                                   HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        accountService.validateOwnership(accountId, dto.playerId());
        return npcService.heal(npcId, dto.playerId());
    }

    @PostMapping("/npcs/{npcId}/warp")
    public NpcWarpResponseDTO warp(@PathVariable Long npcId,
                                   @RequestBody NpcWarpRequestDTO dto,
                                   HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        accountService.validateOwnership(accountId, dto.playerId());
        return npcService.warp(npcId, dto.playerId(), dto.destination());
    }
}
