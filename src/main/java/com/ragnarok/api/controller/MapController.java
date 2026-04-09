package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.TravelRequestDTO;
import com.ragnarok.api.dto.response.MapInfoResponseDTO;
import com.ragnarok.api.dto.response.WalkResponseDTO;
import com.ragnarok.application.dto.WalkResult;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Map", description = "World navigation")
public class MapController {

    private final MapService mapService;
    private final AccountService accountService;

    public MapController(MapService mapService, AccountService accountService) {
        this.mapService = mapService;
        this.accountService = accountService;
    }

    @GetMapping("/api/players/{playerId}/map")
    @Operation(summary = "Get current map and available portals")
    public MapInfoResponseDTO getMapInfo(@PathVariable Long playerId, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        String map = mapService.getCurrentMap(playerId);
        return new MapInfoResponseDTO(map, mapService.getDisplayName(map), mapService.getPortals(map));
    }

    @GetMapping("/api/maps/{mapId}/portals")
    @Operation(summary = "List portals available from a specific map")
    public List<String> getPortals(@PathVariable String mapId) {
        return mapService.getPortals(mapId);
    }

    @PostMapping("/api/players/{playerId}/map/walk")
    @Operation(summary = "Walk in the current map — 70% chance of monster encounter")
    public ResponseEntity<WalkResponseDTO> walk(@PathVariable Long playerId, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        WalkResult result = mapService.walk(playerId);
        return ResponseEntity.ok(new WalkResponseDTO(
                result.encounterOccurred(), result.monsterId(),
                result.monsterName(), result.monsterHp(), result.message()));
    }

    @PostMapping("/api/players/{playerId}/map/travel")
    @Operation(summary = "Travel to another map via portal")
    public ResponseEntity<Void> travel(@PathVariable Long playerId, @RequestBody TravelRequestDTO req, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        mapService.travel(playerId, req.destination());
        return ResponseEntity.ok().build();
    }
}
