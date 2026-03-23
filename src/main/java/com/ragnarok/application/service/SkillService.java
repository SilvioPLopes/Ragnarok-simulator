package com.ragnarok.application.service;

import com.ragnarok.application.dto.SkillRowDTO;
import com.ragnarok.domain.exception.*;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.BuffSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gerencia a árvore de skills do player: listar, aprender e aplicar passivas.
 * Para o uso de skills em combate, veja {@link SkillCombatService}.
 */
@Service
public class SkillService {

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;
    private final PlayerSkillRepository playerSkillRepository;
    private final SkillRepository skillRepository;
    private final ScriptInterpreter scriptInterpreter;
    private final SkillBuffEffectRepository skillBuffEffectRepository;
    private final BuffSerializer buffSerializer;

    public SkillService(PlayerRepository playerRepository,
                        SkillTreeRepository skillTreeRepository,
                        PlayerSkillRepository playerSkillRepository,
                        SkillRepository skillRepository,
                        ScriptInterpreter scriptInterpreter,
                        SkillBuffEffectRepository skillBuffEffectRepository,
                        BuffSerializer buffSerializer) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
        this.playerSkillRepository = playerSkillRepository;
        this.skillRepository = skillRepository;
        this.scriptInterpreter = scriptInterpreter;
        this.skillBuffEffectRepository = skillBuffEffectRepository;
        this.buffSerializer = buffSerializer;
    }

    public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow(() -> new GameException("Player not found: " + playerId));
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
        String jobClass = player.getJobClass();

        List<String> classChain = resolveClassChain(jobClass);
        if (classChain.isEmpty()) return Collections.emptyList();

        List<SkillTreeEntity> todasLinhas = skillTreeRepository.findByJobClassesIn(classChain);
        if (todasLinhas.isEmpty()) return Collections.emptyList();

        Map<String, Integer> playerSkillLevels = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .collect(Collectors.toMap(PlayerSkillEntity::getSkillId, PlayerSkillEntity::getCurrentLevel));

        Map<String, List<SkillTreeEntity>> porSkill = todasLinhas.stream()
                .collect(Collectors.groupingBy(SkillTreeEntity::getSkillId));

        List<SkillRowDTO> resultado = new ArrayList<>();

        for (Map.Entry<String, List<SkillTreeEntity>> entry : porSkill.entrySet()) {
            String skillId = entry.getKey();
            List<SkillTreeEntity> linhas = entry.getValue();
            int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

            int currentLevel = playerSkillLevels.getOrDefault(skillId, 0);

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

    /** Retorna skills aprendidas usáveis fora de combate (BUFF ou HEAL com target SELF). */
    public List<SkillRowDTO> listarSkillsUsaveisForaDeCombate(Long playerId) {
        List<PlayerSkillEntity> aprendidas = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .filter(ps -> ps.getCurrentLevel() > 0)
                .toList();

        List<SkillRowDTO> resultado = new ArrayList<>();
        for (PlayerSkillEntity ps : aprendidas) {
            skillRepository.findByAegisName(ps.getSkillId()).ifPresent(skill -> {
                String et = skill.getEffectType();
                String tt = skill.getTargetType();
                boolean isUsavel = ("BUFF".equalsIgnoreCase(et) || "HEAL".equalsIgnoreCase(et))
                        && (tt == null || "SELF".equalsIgnoreCase(tt));
                if (isUsavel) {
                    resultado.add(new SkillRowDTO(ps.getSkillId(), ps.getSkillId(),
                            0, ps.getCurrentLevel(), true, null));
                }
            });
        }
        resultado.sort(Comparator.comparing(SkillRowDTO::aegisName));
        return resultado;
    }

    @Transactional
    public String aprenderSkill(Long playerId, String aegisName) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow(() -> new GameException("Player not found: " + playerId));
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;

        if (skillPoints <= 0) {
            throw new InsufficientSkillPointsException();
        }

        List<String> classChain = resolveClassChain(player.getJobClass());
        List<SkillTreeEntity> linhas = skillTreeRepository
                .findByJobClassesInAndSkillId(classChain, aegisName);

        if (linhas.isEmpty()) {
            throw new SkillNotFoundException(aegisName, player.getJobClass());
        }

        int maxLevel = linhas.get(0).getMaxLevel() != null ? linhas.get(0).getMaxLevel() : 1;

        Map<String, Integer> playerSkillLevels = playerSkillRepository.findByPlayerId(playerId)
                .stream()
                .collect(Collectors.toMap(PlayerSkillEntity::getSkillId, PlayerSkillEntity::getCurrentLevel));

        int currentLevel = playerSkillLevels.getOrDefault(aegisName, 0);

        if (currentLevel >= maxLevel) {
            throw new SkillMaxLevelException(aegisName, maxLevel);
        }

        for (SkillTreeEntity linha : linhas) {
            if (linha.getPrereqSkill() != null && !linha.getPrereqSkill().isBlank()) {
                int prereqLevel = linha.getPrereqLevel() != null ? linha.getPrereqLevel() : 1;
                int playerPrereqLevel = playerSkillLevels.getOrDefault(linha.getPrereqSkill(), 0);
                if (playerPrereqLevel < prereqLevel) {
                    throw new SkillPrerequisiteException(linha.getPrereqSkill(), prereqLevel);
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

        // Aplica bônus permanente de passiva imediatamente
        SkillEntity skillEntity = skillRepository.findByAegisName(aegisName).orElse(null);
        List<String> passiveDesc = new ArrayList<>();
        if (skillEntity != null && "PASSIVE".equalsIgnoreCase(skillEntity.getEffectType())) {
            List<SkillBuffEffectEntity> passiveEffects = skillBuffEffectRepository.findBySkillId(skillEntity.getId());
            if (!passiveEffects.isEmpty()) {
                List<ActiveBuff> activeBuffs = buffSerializer.fromJson(player.getActiveBuffsJson());
                activeBuffs.removeIf(b -> aegisName.equalsIgnoreCase(b.getSkillAegisName()));
                Map<String, Integer> vars = Map.of("skill_lv", playerSkill.getCurrentLevel());
                for (SkillBuffEffectEntity effect : passiveEffects) {
                    int value = scriptInterpreter.evaluateFormula(effect.getValueFormula(), vars);
                    StatType statType = StatType.valueOf(effect.getStatType());
                    activeBuffs.add(new ActiveBuff(aegisName, statType, value, -1));
                    passiveDesc.add(effect.getStatType() + " +" + value);
                }
                player.setActiveBuffsJson(buffSerializer.toJson(activeBuffs));
            }
        }

        playerRepository.save(player);

        String msg = aegisName + " agora está no nível " + playerSkill.getCurrentLevel();
        if (!passiveDesc.isEmpty()) {
            msg += " [Passiva: " + String.join(", ", passiveDesc) + "]";
        }
        return msg;
    }

    /**
     * Retorna a cadeia completa de classes do player em maiúsculas.
     * Ex: LORD_KNIGHT → ["LORD_KNIGHT", "KNIGHT", "SWORDSMAN"]
     * Permite que o player veja e aprenda skills de todas as classes anteriores.
     */
    private List<String> resolveClassChain(String jobClassName) {
        if (jobClassName == null || jobClassName.isBlank()) return Collections.emptyList();
        List<String> chain = new ArrayList<>();
        try {
            JobClass jc = JobClass.valueOf(jobClassName.toUpperCase());
            while (jc != null) {
                chain.add(jc.name());
                jc = jc.parentClass;
            }
        } catch (IllegalArgumentException e) {
            chain.add(jobClassName.toUpperCase());
        }
        return chain;
    }
}
