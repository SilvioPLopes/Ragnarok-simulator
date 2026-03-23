package com.ragnarok.api.controller;

import com.ragnarok.api.dto.response.InventoryItemResponseDTO;
import com.ragnarok.api.dto.response.SkillUseResponseDTO;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Inventory", description = "Player inventory management")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/api/players/{playerId}/inventory")
    @Operation(summary = "List player inventory")
    public List<InventoryItemResponseDTO> getInventory(@PathVariable Long playerId) {
        return itemService.listarInventario(playerId).stream().map(this::toDTO).toList();
    }

    @PostMapping("/api/players/{playerId}/inventory/{itemId}/use")
    @Operation(summary = "Use an item from inventory")
    public ResponseEntity<SkillUseResponseDTO> useItem(
            @PathVariable Long playerId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(new SkillUseResponseDTO(itemService.usarItem(itemId)));
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
