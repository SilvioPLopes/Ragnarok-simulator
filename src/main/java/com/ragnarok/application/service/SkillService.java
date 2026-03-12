package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;
    private final PlayerSkillRepository playerSkillRepository;

    public SkillService(PlayerRepository playerRepository,
                        SkillTreeRepository skillTreeRepository,
                        PlayerSkillRepository playerSkillRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
        this.playerSkillRepository = playerSkillRepository;
    }

    public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
        String jobClass = player.getJobClass();

        List<SkillTreeEntity> todasLinhas = skillTreeRepository.findByJobClassIgnoreCase(jobClass);
        if (todasLinhas.isEmpty()) return Collections.emptyList();

        // Carrega todas as skills do player de uma vez (evita N+1)
        Map<String, Integer> playerSkillLevels = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .collect(Collectors.toMap(PlayerSkillEntity::getSkillId, PlayerSkillEntity::getCurrentLevel));

        // Agrupa linhas por skillId (cada skill pode ter múltiplos prereqs)
        Map<String, List<SkillTreeEntity>> porSkill = todasLinhas.stream()
                .collect(Collectors.groupingBy(SkillTreeEntity::getSkillId));

        List<SkillRowDTO> resultado = new ArrayList<>();

        for (Map.Entry<String, List<SkillTreeEntity>> entry : porSkill.entrySet()) {
            String skillId = entry.getKey();
            List<SkillTreeEntity> linhas = entry.getValue();
            int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

            int currentLevel = playerSkillLevels.getOrDefault(skillId, 0);

            // Verifica todos os pré-requisitos (AND-logic, sem filtro de job_class)
            String blockedReason = null;

            if (currentLevel >= maxLevel) {
                blockedReason = "Nivel maximo atingido";
            } else {
                for (SkillTreeEntity linha : linhas) {
                    if (linha.getPrereqSkill() != null && !linha.getPrereqSkill().isBlank()) {
                        int prereqLevel = linha.getPrereqLevel() != null ? linha.getPrereqLevel() : 1;
                        int playerPrereqLevel = playerSkillLevels.getOrDefault(linha.getPrereqSkill(), 0);
                        if (playerPrereqLevel < prereqLevel) {
                            blockedReason = "Requer " + linha.getPrereqSkill() + " Lv" + prereqLevel;
                            break;
                        }
                    }
                }
                if (blockedReason == null && skillPoints <= 0) {
                    blockedReason = "Sem Skill Points";
                }
            }

            resultado.add(new SkillRowDTO(skillId, skillId, maxLevel, currentLevel,
                    blockedReason == null, blockedReason));
        }

        resultado.sort(Comparator.comparing(SkillRowDTO::aegisName));
        return resultado;
    }

    @Transactional
    public String aprenderSkill(Long playerId, String aegisName) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;

        if (skillPoints <= 0) {
            throw new IllegalStateException("Sem Skill Points");
        }

        List<SkillTreeEntity> linhas = skillTreeRepository
                .findByJobClassIgnoreCaseAndSkillId(player.getJobClass(), aegisName);

        if (linhas.isEmpty()) {
            throw new IllegalStateException("Skill " + aegisName + " não encontrada para a classe " + player.getJobClass());
        }

        int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

        Map<String, Integer> playerSkillLevels = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .collect(Collectors.toMap(PlayerSkillEntity::getSkillId, PlayerSkillEntity::getCurrentLevel));

        int currentLevel = playerSkillLevels.getOrDefault(aegisName, 0);

        if (currentLevel >= maxLevel) {
            throw new IllegalStateException("Skill " + aegisName + " já está no nível máximo (" + maxLevel + ")");
        }

        for (SkillTreeEntity linha : linhas) {
            if (linha.getPrereqSkill() != null && !linha.getPrereqSkill().isBlank()) {
                int prereqLevel = linha.getPrereqLevel() != null ? linha.getPrereqLevel() : 1;
                int playerPrereqLevel = playerSkillLevels.getOrDefault(linha.getPrereqSkill(), 0);
                if (playerPrereqLevel < prereqLevel) {
                    throw new IllegalStateException("Requer " + linha.getPrereqSkill() + " Lv" + prereqLevel);
                }
            }
        }

        var playerSkill = playerSkillRepository.findByPlayerIdAndSkillId(playerId, aegisName)
                .orElseGet(() -> {
                    var nova = new PlayerSkillEntity();
                    nova.setPlayerId(playerId);
                    nova.setSkillId(aegisName);
                    nova.setCurrentLevel(0);
                    return nova;
                });

        playerSkill.setCurrentLevel(playerSkill.getCurrentLevel() + 1);
        playerSkillRepository.save(playerSkill);

        player.setSkillPoints(skillPoints - 1);
        playerRepository.save(player);

        return aegisName + " agora está no nível " + playerSkill.getCurrentLevel();
    }
}
