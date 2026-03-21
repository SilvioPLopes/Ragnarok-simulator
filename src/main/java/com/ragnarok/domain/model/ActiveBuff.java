package com.ragnarok.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ActiveBuff {

    private String skillAegisName;
    private StatType statType;       // null se for apenas flags (ex: KNOCKBACK_IMMUNE)
    private int value;               // absoluto (+10 STR) ou percentual (+10 para MAX_HP_PERCENT)
    private int turnosRestantes;
    private Set<BuffFlag> flags;

    public ActiveBuff(String skillAegisName, StatType statType, int value, int turnosRestantes) {
        this.skillAegisName = skillAegisName;
        this.statType = statType;
        this.value = value;
        this.turnosRestantes = turnosRestantes;
        this.flags = EnumSet.noneOf(BuffFlag.class);
    }

    /** turnosRestantes == -1 significa buff permanente (passiva). */
    public boolean isExpired() {
        return turnosRestantes == 0;
    }

    public ActiveBuff decrementar() {
        if (turnosRestantes < 0) return this; // permanente — não decrementa
        return new ActiveBuff(skillAegisName, statType, value, turnosRestantes - 1, flags);
    }
}
