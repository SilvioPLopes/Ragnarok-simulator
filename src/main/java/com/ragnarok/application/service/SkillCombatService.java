package com.ragnarok.application.service;

import com.ragnarok.domain.event.MonsterKilledEvent;
import com.ragnarok.domain.exception.*;
import com.ragnarok.domain.model.*;
import com.ragnarok.domain.service.BattleEngine;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.BuffSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Responsável pelo uso de skills durante o combate.
 * Separado do SkillService para manter SRP: este serviço lida com a
 * interação entre skills, monstros e mecânicas de batalha.
 */
@Service
public class SkillCombatService {

    private static final Logger log = LoggerFactory.getLogger(SkillCombatService.class);

    private final PlayerRepository playerRepository;
    private final SkillRepository skillRepository;
    private final PlayerSkillRepository playerSkillRepository;
    private final MonsterRepository monsterRepository;
    private final ScriptInterpreter scriptInterpreter;
    private final SkillBuffEffectRepository skillBuffEffectRepository;
    private final BuffSerializer buffSerializer;
    private final BattleEngine battleEngine;
    private final MonsterMapper monsterMapper;
    private final WeaponSizeService weaponSizeService;
    private final PlayerItemRepository playerItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillCombatService(PlayerRepository playerRepository,
                              SkillRepository skillRepository,
                              PlayerSkillRepository playerSkillRepository,
                              MonsterRepository monsterRepository,
                              ScriptInterpreter scriptInterpreter,
                              SkillBuffEffectRepository skillBuffEffectRepository,
                              BuffSerializer buffSerializer,
                              BattleEngine battleEngine,
                              MonsterMapper monsterMapper,
                              WeaponSizeService weaponSizeService,
                              PlayerItemRepository playerItemRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.skillRepository = skillRepository;
        this.playerSkillRepository = playerSkillRepository;
        this.monsterRepository = monsterRepository;
        this.scriptInterpreter = scriptInterpreter;
        this.skillBuffEffectRepository = skillBuffEffectRepository;
        this.buffSerializer = buffSerializer;
        this.battleEngine = battleEngine;
        this.monsterMapper = monsterMapper;
        this.weaponSizeService = weaponSizeService;
        this.playerItemRepository = playerItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String usarSkillEmCombate(Long playerId, String aegisName, Long monsterId) {
        log.info("[SkillUse] playerId={} skill={} monsterId={}", playerId, aegisName, monsterId);
        SkillEntity skill = skillRepository.findByAegisName(aegisName)
                .orElseThrow(() -> new SkillNotFoundException(aegisName));

        PlayerSkillEntity playerSkill = playerSkillRepository.findByPlayerIdAndSkillId(playerId, aegisName)
                .filter(ps -> ps.getCurrentLevel() > 0)
                .orElseThrow(() -> new GameException("Você não aprendeu " + aegisName + " ainda."));

        PlayerEntity playerEntity = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        int spAtual = playerEntity.getSpCurrent() != null ? playerEntity.getSpCurrent() : 0;
        int spCusto = skill.getSpCost() != null ? skill.getSpCost() : 10;
        int skillLevel = playerSkill.getCurrentLevel();

        if (spAtual < spCusto) {
            throw new InsufficientSpException(spCusto, spAtual);
        }

        log.info("[SkillUse] skill found: aegis={} effectType={} spCost={} targetType={}",
                skill.getAegisName(), skill.getEffectType(), spCusto, skill.getTargetType());

        if ("PASSIVE".equalsIgnoreCase(skill.getEffectType())) {
            throw new GameException(aegisName + " é uma skill passiva e é aplicada automaticamente.");
        }

        playerEntity.setSpCurrent(spAtual - spCusto);

        String effectType = skill.getEffectType();

        // --- DANO FÍSICO ou MÁGICO ---
        if ("PHYSICAL_DAMAGE".equalsIgnoreCase(effectType) || "MAGICAL_DAMAGE".equalsIgnoreCase(effectType)) {
            if (monsterId == null) throw new GameException("Esta skill requer um alvo.");

            MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                    .orElseThrow(() -> new GameException("Monstro não encontrado."));

            Map<String, Integer> vars = buildStatsMap(playerEntity, skillLevel);
            int rawDamage = scriptInterpreter.evaluateFormula(skill.getDamageFormula(), vars);

            if (rawDamage <= 0) {
                EffectResult legacyResult = scriptInterpreter.interpret(skill.getScript(), skillLevel);
                if (legacyResult.supported() && legacyResult.damage() > 0) {
                    rawDamage = legacyResult.damage();
                } else {
                    throw new GameException("Esta skill não pode ser usada ainda.");
                }
            }

            Monster monster = monsterMapper.toDomain(monsterEntity);
            int finalDamage = battleEngine.applyElementModifier(rawDamage, skill.getElement(), monster);

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

            if (novoHp <= 0) {
                log.info("[SkillCombat] Player {} derrotou {} com {}.", playerId, monster.getName(), aegisName);
                List<Item> loot = battleEngine.calculateLoot(monster);
                long baseExp = monster.getBaseExp() != null ? monster.getBaseExp() : 0L;
                long jobExp  = monster.getJobExp()  != null ? monster.getJobExp()  : 0L;
                eventPublisher.publishEvent(new MonsterKilledEvent(playerId, monsterId, loot, baseExp, jobExp));
                String dropLog = loot.isEmpty() ? "" :
                        "\nDrop: " + loot.stream().map(Item::getName).collect(Collectors.joining(", "));
                return String.format("\uD83C\uDF1F VITÓRIA via %s%s! O %s foi derrotado com %d de dano.%s",
                        aegisName, elementInfo, monster.getName(), finalDamage, dropLog);
            }

            return String.format("Você usou %s%s e causou %d de dano. HP do monstro: %d.",
                    aegisName, elementInfo, finalDamage, novoHp);
        }

        // --- CURA ---
        if ("HEAL".equalsIgnoreCase(effectType)) {
            int hpMax = playerEntity.getHpMax() != null ? playerEntity.getHpMax() : 100;

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
                throw new GameException("Buff " + aegisName + " não tem efeitos configurados ainda.");
            }

            List<ActiveBuff> buffsAtivos = buffSerializer.fromJson(playerEntity.getActiveBuffsJson());
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

        // --- Fallback legado ---
        EffectResult result = scriptInterpreter.interpret(skill.getScript(), skillLevel);
        if (!result.supported()) {
            throw new GameException("Esta skill não pode ser usada ainda.");
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
            MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                    .orElseThrow(() -> new GameException("Monstro não encontrado."));
            int hpAtual = monsterEntity.getHp() != null ? monsterEntity.getHp() : 0;
            int novoHp = Math.max(0, hpAtual - result.damage());
            monsterEntity.setHp(novoHp);
            monsterRepository.save(monsterEntity);
            playerRepository.save(playerEntity);

            if (novoHp <= 0) {
                log.info("[SkillCombat] Player {} derrotou monstro {} com {} (fallback).", playerId, monsterId, aegisName);
                Monster monster = monsterMapper.toDomain(monsterEntity);
                List<Item> loot = battleEngine.calculateLoot(monster);
                long baseExp = monster.getBaseExp() != null ? monster.getBaseExp() : 0L;
                long jobExp  = monster.getJobExp()  != null ? monster.getJobExp()  : 0L;
                eventPublisher.publishEvent(new MonsterKilledEvent(playerId, monsterId, loot, baseExp, jobExp));
                String dropLog = loot.isEmpty() ? "" :
                        "\nDrop: " + loot.stream().map(Item::getName).collect(Collectors.joining(", "));
                return String.format("\uD83C\uDF1F VITÓRIA via %s! Causou %d de dano e derrotou o monstro.%s",
                        aegisName, result.damage(), dropLog);
            }

            return String.format("Você usou %s e causou %d de dano. HP do monstro: %d.",
                    aegisName, result.damage(), novoHp);
        }

        playerRepository.save(playerEntity);
        return aegisName + " foi usada (sem efeito imediato).";
    }

    private Map<String, Integer> buildStatsMap(PlayerEntity p, int skillLevel) {
        int str = orZero(p.getStr());
        int agi = orZero(p.getAgi());
        int vit = orZero(p.getVit());
        int intVal = orZero(p.getIntelligence());
        int dex = orZero(p.getDex());
        int luk = orZero(p.getLuk());
        int hpMax = p.getHpMax() != null ? p.getHpMax() : 100;
        int spMax = p.getSpMax() != null ? p.getSpMax() : 40;

        Map<String, Integer> vars = new HashMap<>();
        vars.put("STR", str);
        vars.put("AGI", agi);
        vars.put("VIT", vit);
        vars.put("INT", intVal);
        vars.put("DEX", dex);
        vars.put("LUK", luk);
        vars.put("ATK", str * 2);
        vars.put("MATK", intVal * 2);
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
}
