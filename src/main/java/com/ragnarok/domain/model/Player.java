package com.ragnarok.domain.model;

import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Player {

    private Long id;
    private String name;
    private String jobClass;
    private String gender;
    private Integer baseLevel;
    private Integer jobLevel;
    private Long baseExp;
    private Long jobExp;
    private Long zenny;
    private Integer statPoints;
    private Integer skillPoints;
    private Integer hpCurrent;
    private Integer spCurrent;
    private PlayerStats stats;
    private PlayerLocation location;
    private List<PlayerItem> inventory;
    private List<ActiveBuff> activeBuffs = new ArrayList<>();

    public List<PlayerItem> getEquipments() {
        if (inventory == null) return new ArrayList<>();
        return inventory.stream().filter(item -> Boolean.TRUE.equals(item.getIsEquipped())).toList();
    }

    // --- Buff helpers ---

    public int getBuffBonus(StatType statType) {
        if (activeBuffs == null) return 0;
        return activeBuffs.stream()
                .filter(b -> !b.isExpired() && statType == b.getStatType())
                .mapToInt(ActiveBuff::getValue)
                .sum();
    }

    public boolean hasBuffFlag(BuffFlag flag) {
        if (activeBuffs == null) return false;
        return activeBuffs.stream()
                .anyMatch(b -> !b.isExpired() && b.getFlags().contains(flag));
    }

    public void decrementarBuffs() {
        if (activeBuffs == null) return;
        activeBuffs = activeBuffs.stream()
                .map(ActiveBuff::decrementar)
                .collect(Collectors.toList());
        purgarBuffsExpirados();
    }

    public void purgarBuffsExpirados() {
        if (activeBuffs != null) {
            activeBuffs.removeIf(ActiveBuff::isExpired);
        }
    }

    // --- Stats com modificadores de equipamento + buff ---

    public Integer getTotalStr() {
        int base = (stats != null && stats.getStr() != null) ? stats.getStr() : 0;
        if (inventory == null) return base + getBuffBonus(StatType.STR);
        int bonusEquip = inventory.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsEquipped()))
                .mapToInt(i -> {
                    if (i.getItemDefinition() != null && i.getItemDefinition().getStats() != null) {
                        Integer b = i.getItemDefinition().getStats().getBonusStr();
                        return b != null ? b : 0;
                    }
                    return 0;
                }).sum();
        return base + bonusEquip + getBuffBonus(StatType.STR);
    }

    public Integer getTotalVit() {
        int base = (stats != null && stats.getVit() != null) ? stats.getVit() : 0;
        int bonusEquip = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusVit() != null)
                        ? i.getItemDefinition().getStats().getBonusVit() : 0).sum();
        return base + bonusEquip + getBuffBonus(StatType.VIT);
    }

    public Integer getTotalInt() {
        int base = (stats != null && stats.getIntVal() != null) ? stats.getIntVal() : 0;
        int bonusEquip = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusInt() != null)
                        ? i.getItemDefinition().getStats().getBonusInt() : 0).sum();
        return base + bonusEquip + getBuffBonus(StatType.INT);
    }

    public Integer getTotalDex() {
        int base = (stats != null && stats.getDex() != null) ? stats.getDex() : 0;
        int bonusEquip = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusDex() != null)
                        ? i.getItemDefinition().getStats().getBonusDex() : 0).sum();
        return base + bonusEquip + getBuffBonus(StatType.DEX);
    }

    public Integer getTotalAgi() {
        int base = (stats != null && stats.getAgi() != null) ? stats.getAgi() : 0;
        int bonusEquip = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusAgi() != null)
                        ? i.getItemDefinition().getStats().getBonusAgi() : 0).sum();
        return base + bonusEquip + getBuffBonus(StatType.AGI);
    }

    public Integer getTotalLuk() {
        int base = (stats != null && stats.getLuk() != null) ? stats.getLuk() : 0;
        int bonusEquip = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusLuk() != null)
                        ? i.getItemDefinition().getStats().getBonusLuk() : 0).sum();
        return base + bonusEquip + getBuffBonus(StatType.LUK);
    }

    public Integer getMaxHp() {
        int base = (stats != null && stats.getMaxHp() != null) ? stats.getMaxHp() : 100;
        int pct = getBuffBonus(StatType.MAX_HP_PERCENT);
        return base + (base * pct / 100);
    }

    public Integer getMaxSp() {
        return this.stats != null && this.stats.getMaxSp() != null ? this.stats.getMaxSp() : 40;
    }

    public Integer getTotalAtk() {
        int statusAtk = getTotalStr() * 2;
        int weaponAtk = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition() != null && i.getItemDefinition().getStats() != null
                        && i.getItemDefinition().getStats().getAttack() != null)
                        ? i.getItemDefinition().getStats().getAttack() : 0)
                .sum();
        return statusAtk + weaponAtk + getBuffBonus(StatType.ATK);
    }

    public Integer getTotalMAtk() {
        int statusMAtk = getTotalInt() * 2;
        int weaponMAtk = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition() != null && i.getItemDefinition().getStats() != null
                        && i.getItemDefinition().getStats().getMAttack() != null)
                        ? i.getItemDefinition().getStats().getMAttack() : 0)
                .sum();
        return statusMAtk + weaponMAtk;
    }

    public Integer getTotalDef() {
        int armorDef = getEquipments().stream()
                .mapToInt(i -> (i.getItemDefinition() != null && i.getItemDefinition().getStats() != null
                        && i.getItemDefinition().getStats().getDefense() != null)
                        ? i.getItemDefinition().getStats().getDefense() : 0)
                .sum();
        return getTotalVit() + armorDef + getBuffBonus(StatType.DEF);
    }

    public Integer getTotalHit() {
        int level = baseLevel != null ? baseLevel : 1;
        return getTotalDex() + level;
    }

    public Integer getTotalFlee() {
        int level = baseLevel != null ? baseLevel : 1;
        return getTotalAgi() + level + getBuffBonus(StatType.FLEE);
    }
}
