package com.ragnarok.domain.model;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

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

    public List<PlayerItem> getEquipments() {
        if (inventory == null) return new ArrayList<>();
        return inventory.stream().filter(item -> Boolean.TRUE.equals(item.getIsEquipped())).toList();
    }
    public Integer getTotalStr() {
        int baseStr = (stats != null && stats.getStr() != null) ? stats.getStr() : 0;
        if (inventory == null) return baseStr;
        int bonusTotal = inventory.stream().filter(playerItem -> Boolean.TRUE.equals(playerItem.getIsEquipped())).mapToInt(playerItem -> {if (playerItem.getItemDefinition() != null && playerItem.getItemDefinition().getStats() != null) {
                        Integer bonus = playerItem.getItemDefinition().getStats().getBonusStr();
                        return (bonus != null) ? bonus : 0;
                    }
                    return 0;
                }).sum();
        return baseStr + bonusTotal;
    }

    public Integer getTotalVit() {
        int base = (stats != null && stats.getVit() != null)? stats.getVit() : 0;
        return base + getEquipments().stream().mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusVit() != null) ? i.getItemDefinition().getStats().getBonusVit() : 0).sum();
    }

    public Integer getTotalInt(){
        int base = (stats != null && stats.getIntVal() != null)? stats.getIntVal() : 0;
        return base + getEquipments().stream().mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusInt() != null) ? i.getItemDefinition().getStats().getBonusInt() : 0).sum();
    }

    public Integer getTotalDex() {
        int base = (stats != null && stats.getDex() != null)? stats.getDex() : 0;
        return base + getEquipments().stream().mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusDex() != null) ? i.getItemDefinition().getStats().getBonusDex() : 0).sum();
    }
    public Integer getTotalAgi() {
        int base = (stats != null && stats.getAgi() != null)? stats.getAgi() : 0;
        return base + getEquipments().stream().mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusAgi() != null) ? i.getItemDefinition().getStats().getBonusAgi() : 0).sum();
    }
    public Integer getTotalLuk() {
        int base = (stats != null && stats.getLuk() != null)? stats.getLuk() : 0;
        return base + getEquipments().stream().mapToInt(i -> (i.getItemDefinition().getStats() != null && i.getItemDefinition().getStats().getBonusLuk() != null) ? i.getItemDefinition().getStats().getBonusLuk() : 0).sum();
    }
}