package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.UseSkillRequestDTO;
import com.ragnarok.api.dto.response.SkillRowResponseDTO;
import com.ragnarok.api.dto.response.SkillUseResponseDTO;
import com.ragnarok.application.dto.SkillRowDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.SkillCombatService;
import com.ragnarok.application.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players/{playerId}/skills")
@Tag(name = "Skills", description = "Skill tree and combat skill usage")
public class SkillController {

    private final SkillService skillService;
    private final SkillCombatService skillCombatService;
    private final AccountService accountService;

    public SkillController(SkillService skillService, SkillCombatService skillCombatService, AccountService accountService) {
        this.skillService = skillService;
        this.skillCombatService = skillCombatService;
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List all skills available to the player")
    public List<SkillRowResponseDTO> listSkills(@PathVariable Long playerId, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        return skillService.listarSkillsDoPlayer(playerId).stream().map(this::toDTO).toList();
    }

    @PostMapping("/{skillName}/learn")
    @Operation(summary = "Learn or level up a skill")
    public ResponseEntity<SkillUseResponseDTO> learnSkill(
            @PathVariable Long playerId, @PathVariable String skillName, HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        return ResponseEntity.ok(new SkillUseResponseDTO(skillService.aprenderSkill(playerId, skillName)));
    }

    @PostMapping("/{skillName}/use")
    @Operation(summary = "Use a skill (optionally targeting a monster)")
    public ResponseEntity<SkillUseResponseDTO> useSkill(
            @PathVariable Long playerId,
            @PathVariable String skillName,
            @RequestBody(required = false) UseSkillRequestDTO req,
            HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("accountId");
        if (accountId != null) {
            accountService.validateOwnership(accountId, playerId);
        }
        Long monsterId = req != null ? req.monsterId() : null;
        String result = skillCombatService.usarSkillEmCombate(playerId, skillName, monsterId);
        return ResponseEntity.ok(new SkillUseResponseDTO(result));
    }

    private SkillRowResponseDTO toDTO(SkillRowDTO dto) {
        return new SkillRowResponseDTO(dto.aegisName(), dto.name(),
                dto.maxLevel(), dto.currentLevel(), dto.canLearn(), dto.blockedReason());
    }
}
