package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.api.dto.response.BattleResponseDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.BattleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/battle")
@Tag(name = "Battle", description = "Combat actions")
public class BattleController {

    private final BattleService battleService;
    private final AccountService accountService;

    public BattleController(BattleService battleService, AccountService accountService) {
        this.battleService = battleService;
        this.accountService = accountService;
    }

    @PostMapping("/attack")
    @Operation(summary = "Perform a basic attack against a monster")
    public ResponseEntity<BattleResponseDTO> attack(@RequestBody AttackRequestDTO req, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, req.playerId());
        }
        BattleService.AttackResult result = battleService.realizarAtaque(req.playerId(), req.monsterId());
        return ResponseEntity.ok(new BattleResponseDTO(result.message(), result.monsterHpRemaining()));
    }
}
