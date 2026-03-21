package com.ragnarok.domain.service;

import com.ragnarok.domain.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BattleEngine {

    public int calculateDamage(Player attacker, Monster target) {
        if (attacker == null || target == null) {
            throw new IllegalArgumentException("Attacker and Target cannot be null");
        }

        int totalStr = attacker.getTotalStr();
        int statusAtk = totalStr * 2;

        int weaponAtk = 0;
        for (PlayerItem equip : attacker.getEquipments()) {
            if (equip.getItemDefinition() != null && equip.getItemDefinition().getStats() != null) {
                Integer wAtk = equip.getItemDefinition().getStats().getAttack();
                weaponAtk += (wAtk != null) ? wAtk : 0;
            }
        }

        int totalAtk = statusAtk + weaponAtk;
        int targetDef = target.getStats() != null ? target.getStats().getDef() : 0;
        int damage = Math.max(1, totalAtk - targetDef);

        // Modificador percentual de ATK (ex: Magnum Break +10%)
        int atkPercent = attacker.getBuffBonus(StatType.ATK_PERCENT);
        if (atkPercent > 0) {
            damage = damage + (damage * atkPercent / 100);
        }

        return damage;
    }

    /**
     * Calcula dano de skill com fórmula personalizada e modificador elemental.
     *
     * @param rawDamage   dano calculado pela fórmula da skill (via ScriptInterpreter)
     * @param element     elemento da skill (ex: "FIRE", "WATER", "NEUTRAL")
     * @param target      monstro alvo
     * @return            dano final ajustado pelo elemento
     */
    public int applyElementModifier(int rawDamage, String element, Monster target) {
        if (element == null || element.isBlank() || "NEUTRAL".equalsIgnoreCase(element)) {
            return Math.max(1, rawDamage);
        }

        int modifier = getElementResistance(target, element);
        // modifier é porcentagem: 100 = normal, 150 = 50% mais dano, 50 = metade do dano
        int finalDamage = rawDamage * modifier / 100;
        return Math.max(1, finalDamage);
    }

    /**
     * Retorna a resistência do monstro ao elemento da skill (em %).
     * 100 = dano normal, 150 = 50% a mais, 50 = metade, 0 = imune.
     */
    private int getElementResistance(Monster target, String element) {
        if (target.getElementalDamage() == null) return 100;

        ElementalDamage res = target.getElementalDamage();
        Integer value = switch (element.toUpperCase()) {
            case "FIRE"    -> res.getFire();
            case "WATER"   -> res.getWater();
            case "WIND"    -> res.getWind();
            case "EARTH"   -> res.getEarth();
            case "HOLY"    -> res.getHoly();
            case "SHADOW"  -> res.getShadow();
            case "GHOST"   -> res.getGhost();
            case "UNDEAD"  -> res.getUndead();
            case "POISON"  -> res.getPoison();
            default        -> res.getNeutral();
        };

        return value != null ? value : 100;
    }

    /**
     * Aplica o modificador de tamanho de arma sobre o dano calculado.
     * @param damage       dano base
     * @param modifierPct  modificador em % (100 = normal, 75 = -25%, 50 = -50%)
     */
    public int applyWeaponSizeModifier(int damage, int modifierPct) {
        return Math.max(1, damage * modifierPct / 100);
    }

    public List<Item> calculateLoot(Monster monster) {
        if (monster == null) throw new IllegalArgumentException("Monster cannot be null for loot calculation");
        List<Item> droppedItems = new ArrayList<>();

        if (monster.getDrops() == null) return droppedItems;

        for (MonsterDrop drop : monster.getDrops()) {
            double roll = ThreadLocalRandom.current().nextDouble(0, 100);
            if (roll <= drop.getRate()) {
                droppedItems.add(drop.getItem());
            }
        }
        return droppedItems;
    }

    public int calculateMonsterDamage(Monster attacker, Player target) {
        if (attacker == null || target == null) {
            throw new IllegalArgumentException("Cannot calculate damage with null Attacker or Target");
        }

        int monsterAtk = attacker.getStats() != null ? attacker.getStats().getAttack() : 0;
        int playerDef = target.getTotalDef();
        int damage = monsterAtk - playerDef;
        return Math.max(1, damage);
    }
}
