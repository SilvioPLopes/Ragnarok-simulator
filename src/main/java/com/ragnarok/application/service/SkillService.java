package com.ragnarok.application.service;

import com.ragnarok.domain.model.EffectResult;
import com.ragnarok.domain.service.ScriptInterpreter;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private static final int SP_CUSTO_SKILL = 10;

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;
    private final PlayerSkillRepository playerSkillRepository;
    private final SkillRepository skillRepository;
    private final ScriptInterpreter scriptInterpreter = new ScriptInterpreter();

    public SkillService(PlayerRepository playerRepository,
                        SkillTreeRepository skillTreeRepository,
                        PlayerSkillRepository playerSkillRepository,
                        SkillRepository skillRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
        this.playerSkillRepository = playerSkillRepository;
        this.skillRepository = skillRepository;
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

    @Transactional
    public String usarSkillEmCombate(Long playerId, String aegisName, Long monsterId) {
        SkillEntity skill = skillRepository.findByAegisName(aegisName)
                .orElseThrow(() -> new IllegalStateException("Skill " + aegisName + " não encontrada."));

        playerSkillRepository.findByPlayerIdAndSkillId(playerId, aegisName)
                .filter(ps -> ps.getCurrentLevel() > 0)
                .orElseThrow(() -> new IllegalStateException("Você não aprendeu " + aegisName + " ainda."));

        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int spAtual = player.getSpCurrent() != null ? player.getSpCurrent() : 0;

        if (spAtual < SP_CUSTO_SKILL) {
            throw new IllegalStateException("SP insuficiente. Necessário: " + SP_CUSTO_SKILL + ", atual: " + spAtual);
        }

        EffectResult result = scriptInterpreter.interpret(skill.getScript());

        if (!result.supported()) {
            throw new IllegalStateException("Esta skill não pode ser usada ainda.");
        }

        String mensagem;

        if (result.hpHeal() > 0) {
            int hpAtual = player.getHpCurrent() != null ? player.getHpCurrent() : 0;
            int hpMax   = player.getHpMax()     != null ? player.getHpMax()     : 100;
            int novoHp  = Math.min(hpMax, hpAtual + result.hpHeal());
            int curado  = novoHp - hpAtual;
            player.setHpCurrent(novoHp);
            mensagem = String.format("Você usou %s e recuperou %d de HP.", aegisName, curado);
        } else {
            mensagem = aegisName + " foi usada (sem efeito imediato).";
        }

        player.setSpCurrent(spAtual - SP_CUSTO_SKILL);
        playerRepository.save(player);

        return mensagem;
    }
}
