package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.CreatePlayerRequestDTO;
import com.ragnarok.api.dto.response.PlayerResponseDTO;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players", description = "Player management")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    @Operation(summary = "List all players")
    public List<PlayerResponseDTO> listPlayers() {
        return playerService.listarPersonagens().stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player by ID")
    public ResponseEntity<PlayerResponseDTO> getPlayer(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(playerService.buscarPersonagem(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new player")
    public ResponseEntity<PlayerResponseDTO> createPlayer(@RequestBody CreatePlayerRequestDTO req) {
        Player created = playerService.criarNovoPersonagem(req.name(), req.jobClass());
        // Player domain model uses getMaxHp()/getMaxSp() (not getHpMax/getSpMax)
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlayerResponseDTO(
                        created.getId(), created.getName(), created.getJobClass(),
                        created.getBaseLevel(), created.getJobLevel(),
                        created.getHpCurrent(), created.getMaxHp(),
                        created.getSpCurrent(), created.getMaxSp(),
                        null, null, null, null, null, null,
                        null, null, created.getZenny(), null));
    }

    // PlayerEntity uses getIntelligence() for the int_val column field
    private PlayerResponseDTO toDTO(PlayerEntity e) {
        return new PlayerResponseDTO(
                e.getId(), e.getName(), e.getJobClass(),
                e.getBaseLevel(), e.getJobLevel(),
                e.getHpCurrent(), e.getHpMax(),
                e.getSpCurrent(), e.getSpMax(),
                e.getStr(), e.getAgi(), e.getVit(), e.getIntelligence(),
                e.getDex(), e.getLuk(),
                e.getStatPoints(), e.getSkillPoints(),
                e.getZenny(), e.getMapName());
    }
}
