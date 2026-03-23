package com.ragnarok.application.service;

import com.ragnarok.domain.model.*;
import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.BuffSerializer;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.domain.service.BattleEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private static final int SP_CUSTO_PADRAO = 10;

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;
    private final PlayerSkillRepository playerSkillRepository;
    private final SkillRepository skillRepository;
    private final MonsterRepository monsterRepository;
    private final ScriptInterpreter scriptInterpreter;
    private final SkillBuffEffectRepository skillBuffEffectRepository;
    private final BuffSerializer buffSerializer;
    private final BattleEngine battleEngine;
    private final MonsterMapper monsterMapper;
    private final WeaponSizeService weaponSizeService;
    private final PlayerItemRepository playerItemRepository;

    public SkillService(PlayerRepository playerRepository,
                        SkillTreeRepository skillTreeRepository,
                        PlayerSkillRepository playerSkillRepository,
                        SkillRepository skillRepository,
                        MonsterRepository monsterRepository,
                        ScriptInterpreter scriptInterpreter,
                        SkillBuffEffectRepository skillBuffEffectRepository,
                        BuffSerializer buffSerializer,
                        BattleEngine battleEngine,
                        MonsterMapper monsterMapper,
                        WeaponSizeService weaponSizeService,
                        PlayerItemRepository playerItemRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
        this.playerSkillRepository = playerSkillRepository;
        this.skillRepository = skillRepository;
        this.monsterRepository = monsterRepository;
        this.scriptInterpreter = scriptInterpreter;
        this.skillBuffEffectRepository = skillBuffEffectRepository;
        this.buffSerializer = buffSerializer;
        this.battleEngine = battleEngine;
        this.monsterMapper = monsterMapper;
        this.weaponSizeService = weaponSizeService;
        this.playerItemRepository = playerItemRepository;
    }

    public List<SkillRowDTO> listarSkillsDoPlayer(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
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
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        int skillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;

        if (skillPoints <= 0) {
            throw new IllegalStateException("Sem Skill Points");
        }

        List<String> classChain = resolveClassChain(player.getJobClass());
        List<SkillTreeEntity> linhas = skillTreeRepository
                .findByJobClassesInAndSkillId(classChain, aegisName);

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

    @Transactional
    public String usarSkillEmCombate(Long playerId, String aegisName, Long monsterId) {
        SkillEntity skill = skillRepository.findByAegisName(aegisName)
                .orElseThrow(() -> new IllegalStateException("Skill " + aegisName + " não encontrada."));

        PlayerSkillEntity playerSkill = playerSkillRepository.findByPlayerIdAndSkillId(playerId, aegisName)
                .filter(ps -> ps.getCurrentLevel() > 0)
                .orElseThrow(() -> new IllegalStateException("Você não aprendeu " + aegisName + " ainda."));

        PlayerEntity playerEntity = playerRepository.findById(playerId).orElseThrow();
        int spAtual = playerEntity.getSpCurrent() != null ? playerEntity.getSpCurrent() : 0;
        int spCusto = skill.getSpCost() != null ? skill.getSpCost() : SP_CUSTO_PADRAO;
        int skillLevel = playerSkill.getCurrentLevel();

        if (spAtual < spCusto) {
            throw new IllegalStateException("SP insuficiente. Necessário: " + spCusto + ", atual: " + spAtual);
        }

        // Skill passiva não pode ser usada manualmente
        if ("PASSIVE".equalsIgnoreCase(skill.getEffectType())) {
            throw new IllegalStateException(aegisName + " é uma skill passiva e é aplicada automaticamente.");
        }

        playerEntity.setSpCurrent(spAtual - spCusto);

        String effectType = skill.getEffectType();

        // --- DANO FÍSICO ou MÁGICO ---
        if ("PHYSICAL_DAMAGE".equalsIgnoreCase(effectType) || "MAGICAL_DAMAGE".equalsIgnoreCase(effectType)) {
            if (monsterId == null) throw new IllegalStateException("Esta skill requer um alvo.");

            MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                    .orElseThrow(() -> new IllegalStateException("Monstro não encontrado."));

            Map<String, Integer> vars = buildStatsMap(playerEntity, skillLevel);
            int rawDamage = scriptInterpreter.evaluateFormula(skill.getDamageFormula(), vars);

            if (rawDamage <= 0) {
                // Fallback para script legado se não tiver damageFormula
                EffectResult legacyResult = scriptInterpreter.interpret(skill.getScript(), skillLevel);
                if (legacyResult.supported() && legacyResult.damage() > 0) {
                    rawDamage = legacyResult.damage();
                } else {
                    throw new IllegalStateException("Esta skill não pode ser usada ainda.");
                }
            }

            Monster monster = monsterMapper.toDomain(monsterEntity);
            int finalDamage = battleEngine.applyElementModifier(rawDamage, skill.getElement(), monster);

            // Aplica modificador de tamanho da arma
            WeaponType weaponType = playerItemRepository.findByPlayerIdAndEquippedTrue(playerId).stream()
                    .map(pi -> pi.getItem() != null ? pi.getItem().getWeaponType() : null)
                    .filter(wt -> wt != null && wt != WeaponType.NONE)
                    .findFirst()
                    .orElse(WeaponType.NONE);
            int sizeModPct = weaponSizeService.getModifier(weaponType, monster.getSize());
            finalDamage = battleEngine.applyWeaponSizeModifier(finalDamage, sizeModPct);

            int hpAtual = monsterEntity.getHp() != null ? monsterEntity.getHp() : 0;
            int novoHp = Math.max(0, hpAtual - finalDamage);
            monsterEntity.setHp(novoHp);
            monsterRepository.save(monsterEntity);
            playerRepository.save(playerEntity);

            String elementInfo = skill.getElement() != null ? " [" + skill.getElement() + "]" : "";
            return String.format("Você usou %s%s e causou %d de dano. HP do monstro: %d.",
                    aegisName, elementInfo, finalDamage, novoHp);
        }

        // --- CURA ---
        if ("HEAL".equalsIgnoreCase(effectType)) {
            int hpMax = playerEntity.getHpMax() != null ? playerEntity.getHpMax() : 100;
            int spMax = playerEntity.getSpMax() != null ? playerEntity.getSpMax() : 40;

            // Tenta fórmula nova primeiro, senão usa script legado
            int hpHeal = 0;
            if (skill.getDamageFormula() != null && !skill.getDamageFormula().isBlank()) {
                Map<String, Integer> vars = buildStatsMap(playerEntity, skillLevel);
                hpHeal = scriptInterpreter.evaluateFormula(skill.getDamageFormula(), vars);
            } else {
                EffectResult result = scriptInterpreter.interpret(skill.getScript(), skillLevel);
                if (result.supported()) {
                    hpHeal = result.isPercent() ? hpMax * result.hpHeal() / 100 : result.hpHeal();
                }
            }

            int hpAtual = playerEntity.getHpCurrent() != null ? playerEntity.getHpCurrent() : 0;
            int novoHp = Math.min(hpMax, hpAtual + hpHeal);
            int curado = novoHp - hpAtual;
            playerEntity.setHpCurrent(novoHp);
            playerRepository.save(playerEntity);
            return String.format("Você usou %s e recuperou %d de HP.", aegisName, curado);
        }

        // --- BUFF ---
        if ("BUFF".equalsIgnoreCase(effectType)) {
            int durationTurns = skill.getDurationTurns() != null ? skill.getDurationTurns() : 10;

            List<SkillBuffEffectEntity> buffEffects = skillBuffEffectRepository.findBySkillId(skill.getId());
            if (buffEffects.isEmpty()) {
                throw new IllegalStateException("Buff " + aegisName + " não tem efeitos configurados ainda.");
            }

            List<ActiveBuff> buffsAtivos = buffSerializer.fromJson(playerEntity.getActiveBuffsJson());

            // Remove buffs anteriores da mesma skill (re-aplicar refresha duração)
            buffsAtivos.removeIf(b -> aegisName.equalsIgnoreCase(b.getSkillAegisName()));

            Map<String, Integer> vars = Map.of("skill_lv", skillLevel);
            List<String> effectDesc = new ArrayList<>();
            for (SkillBuffEffectEntity effect : buffEffects) {
                int value = scriptInterpreter.evaluateFormula(effect.getValueFormula(), vars);
                StatType statType = StatType.valueOf(effect.getStatType());
                buffsAtivos.add(new ActiveBuff(aegisName, statType, value, durationTurns));
                effectDesc.add(effect.getStatType() + " +" + value);
            }

            playerEntity.setActiveBuffsJson(buffSerializer.toJson(buffsAtivos));
            playerRepository.save(playerEntity);

            return String.format("Você usou %s (Lv%d). Efeito dura %d turnos. [%s]",
                    aegisName, skillLevel, durationTurns, String.join(", ", effectDesc));
        }

        // --- Fallback legado (usa campo script) ---
        EffectResult result = scriptInterpreter.interpret(skill.getScript(), skillLevel);
        if (!result.supported()) {
            throw new IllegalStateException("Esta skill não pode ser usada ainda.");
        }

        if (result.hpHeal() > 0 || result.spHeal() > 0) {
            int hpMax = playerEntity.getHpMax() != null ? playerEntity.getHpMax() : 100;
            int hpHeal = result.isPercent() ? hpMax * result.hpHeal() / 100 : result.hpHeal();
            int hpAtual = playerEntity.getHpCurrent() != null ? playerEntity.getHpCurrent() : 0;
            int novoHp = Math.min(hpMax, hpAtual + hpHeal);
            playerEntity.setHpCurrent(novoHp);
            playerRepository.save(playerEntity);
            return String.format("Você usou %s e recuperou %d de HP.", aegisName, novoHp - hpAtual);
        }

        if (result.damage() > 0 && monsterId != null) {
            MonsterEntity monster = monsterRepository.findById(monsterId)
                    .orElseThrow(() -> new IllegalStateException("Monstro não encontrado."));
            int novoHp = Math.max(0, (monster.getHp() != null ? monster.getHp() : 0) - result.damage());
            monster.setHp(novoHp);
            monsterRepository.save(monster);
            playerRepository.save(playerEntity);
            return String.format("Você usou %s e causou %d de dano. HP do monstro: %d.",
                    aegisName, result.damage(), novoHp);
        }

        playerRepository.save(playerEntity);
        return aegisName + " foi usada (sem efeito imediato).";
    }

    // --- Helpers ---

    private Map<String, Integer> buildStatsMap(PlayerEntity p, int skillLevel) {
        int str = orZero(p.getStr());
        int agi = orZero(p.getAgi());
        int vit = orZero(p.getVit());
        int intVal = orZero(p.getIntelligence());
        int dex = orZero(p.getDex());
        int luk = orZero(p.getLuk());
        int hpMax = p.getHpMax() != null ? p.getHpMax() : 100;
        int spMax = p.getSpMax() != null ? p.getSpMax() : 40;
        int atk = str * 2;
        int mAtk = intVal * 2;

        Map<String, Integer> vars = new HashMap<>();
        vars.put("STR", str);
        vars.put("AGI", agi);
        vars.put("VIT", vit);
        vars.put("INT", intVal);
        vars.put("DEX", dex);
        vars.put("LUK", luk);
        vars.put("ATK", atk);
        vars.put("MATK", mAtk);
        vars.put("MaxHP", hpMax);
        vars.put("MaxSP", spMax);
        vars.put("HP", orZero(p.getHpCurrent()));
        vars.put("SP", orZero(p.getSpCurrent()));
        vars.put("skill_lv", skillLevel);
        return vars;
    }

    private int orZero(Integer val) {
        return val != null ? val : 0;
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
