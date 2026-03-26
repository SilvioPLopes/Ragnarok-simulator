package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.api.dto.response.BattleResponseDTO;
import com.ragnarok.application.service.BattleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/battle")
@Tag(name = "Battle", description = "Combat actions")
public class BattleController {

    private final BattleService battleService;

    public BattleController(BattleService battleService) {
        this.battleService = battleService;
    }

    @PostMapping("/attack")
    @Operation(summary = "Perform a basic attack against a monster")
    public ResponseEntity<BattleResponseDTO> attack(@RequestBody AttackRequestDTO req) {
        String result = battleService.realizarAtaque(req.playerId(), req.monsterId());
        return ResponseEntity.ok(new BattleResponseDTO(result));
    }
}
