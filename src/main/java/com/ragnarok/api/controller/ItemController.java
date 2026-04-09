package com.ragnarok.api.controller;

import com.ragnarok.api.dto.response.InventoryItemResponseDTO;
import com.ragnarok.api.dto.response.SkillUseResponseDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/players/{playerId}/inventory")
@Tag(name = "Inventory", description = "Player inventory management")
public class ItemController {

    private final ItemService itemService;
    private final AccountService accountService;

    public ItemController(ItemService itemService, AccountService accountService) {
        this.itemService = itemService;
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List player inventory")
    public List<InventoryItemResponseDTO> getInventory(@PathVariable Long playerId, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        return itemService.listarInventario(playerId).stream().map(this::toDTO).toList();
    }

    @PostMapping("/{itemId}/use")
    @Operation(summary = "Use an item from inventory")
    public ResponseEntity<SkillUseResponseDTO> useItem(
            @PathVariable Long playerId, @PathVariable UUID itemId, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        return ResponseEntity.ok(new SkillUseResponseDTO(itemService.usarItem(itemId)));
    }

    @PostMapping("/{itemId}/equip")
    @Operation(summary = "Equip or unequip an item (toggle)")
    public ResponseEntity<SkillUseResponseDTO> equipItem(
            @PathVariable Long playerId, @PathVariable UUID itemId, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        return ResponseEntity.ok(new SkillUseResponseDTO(itemService.equiparItem(playerId, itemId)));
    }

    private InventoryItemResponseDTO toDTO(PlayerItemEntity pi) {
        return new InventoryItemResponseDTO(
                pi.getId().toString(),
                pi.getItem() != null ? pi.getItem().getName() : "Unknown",
                pi.getItem() != null && pi.getItem().getType() != null
                        ? pi.getItem().getType().name() : "UNKNOWN",
                pi.getAmount(),
                pi.getEquipped(),
                pi.getItem() != null ? pi.getItem().getImgUrl() : null,
                pi.getItem() != null ? pi.getItem().getDescription() : null);
    }
}
