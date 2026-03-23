package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.api.dto.response.BattleResponseDTO;
import com.ragnarok.application.service.BattleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/battle")
public class BattleController {

    private final BattleService battleService;

    public BattleController(BattleService battleService) {
        this.battleService = battleService;
    }

    @PostMapping("/attack")
    public ResponseEntity<BattleResponseDTO> attack(@RequestBody AttackRequestDTO req) {
        String result = battleService.realizarAtaque(req.playerId(), req.monsterId());
        return ResponseEntity.ok(new BattleResponseDTO(result));
    }
}
