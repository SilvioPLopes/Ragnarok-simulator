package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.ClassChangeRequestDTO;
import com.ragnarok.api.dto.request.CreatePlayerRequestDTO;
import com.ragnarok.api.dto.request.UpdateStatsRequestDTO;
import com.ragnarok.api.dto.response.PlayerResponseDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.ClassChangeService;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.JobClass;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players", description = "Player management")
public class PlayerController {

    private final PlayerService playerService;
    private final AccountService accountService;
    private final ClassChangeService classChangeService;

    public PlayerController(PlayerService playerService, AccountService accountService,
                            ClassChangeService classChangeService) {
        this.playerService = playerService;
        this.accountService = accountService;
        this.classChangeService = classChangeService;
    }

    @GetMapping
    @Operation(summary = "List all players")
    public List<PlayerResponseDTO> listPlayers(HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        return playerService.listarPersonagens(accountId).stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player by ID")
    public ResponseEntity<PlayerResponseDTO> getPlayer(@PathVariable Long id, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, id);
        }
        return ResponseEntity.ok(toDTO(playerService.buscarPersonagem(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new player")
    public ResponseEntity<PlayerResponseDTO> createPlayer(@RequestBody CreatePlayerRequestDTO req,
                                                          HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        Player created = playerService.criarNovoPersonagem(req.name(), req.jobClass(), accountId);
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

    @PutMapping("/{id}/stats")
    @Operation(summary = "Distribute stat points")
    public ResponseEntity<PlayerResponseDTO> updateStats(@PathVariable Long id,
                                                         @RequestBody UpdateStatsRequestDTO req,
                                                         HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) accountService.validateOwnership(accountId, id);
        Map<String, Integer> delta = new java.util.HashMap<>();
        delta.put("str", req.str());
        delta.put("agi", req.agi());
        delta.put("vit", req.vit());
        delta.put("int", req.intel());
        delta.put("dex", req.dex());
        delta.put("luk", req.luk());
        return ResponseEntity.ok(toDTO(playerService.distribuirStats(id, delta)));
    }

    @PostMapping("/{id}/resurrect")
    @Operation(summary = "Resurrect a dead player at Prontera")
    public ResponseEntity<PlayerResponseDTO> resurrect(@PathVariable Long id,
                                                       HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) accountService.validateOwnership(accountId, id);
        playerService.ressuscitarJogador(id);
        return ResponseEntity.ok(toDTO(playerService.buscarPersonagem(id)));
    }

    @GetMapping("/{id}/class-change")
    @Operation(summary = "List available job classes for promotion")
    public List<String> listAvailableClasses(@PathVariable Long id, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) accountService.validateOwnership(accountId, id);
        return classChangeService.listarClassesDisponiveis(id).stream().map(Enum::name).toList();
    }

    @PostMapping("/{id}/class-change")
    @Operation(summary = "Perform a job class change")
    public ResponseEntity<PlayerResponseDTO> changeClass(@PathVariable Long id,
                                                         @RequestBody ClassChangeRequestDTO req,
                                                         HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) accountService.validateOwnership(accountId, id);
        JobClass novaClasse = JobClass.valueOf(req.targetClass().toUpperCase());
        classChangeService.trocarClasse(id, novaClasse);
        return ResponseEntity.ok(toDTO(playerService.buscarPersonagem(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a player character")
    public void deletePlayer(@PathVariable Long id, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) accountService.validateOwnership(accountId, id);
        playerService.deletarPersonagem(id);
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
