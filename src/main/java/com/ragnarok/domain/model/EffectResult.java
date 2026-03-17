package com.ragnarok.domain.model;

public record EffectResult(
        int hpHeal,
        int spHeal,
        int damage,
        boolean supported,
        boolean isPercent
) {
    public static EffectResult heal(int hp, int sp) {
        return new EffectResult(hp, sp, 0, true, false);
    }

    public static EffectResult percentHeal(int hpPct, int spPct) {
        return new EffectResult(hpPct, spPct, 0, true, true);
    }

    public static EffectResult damage(int value) {
        return new EffectResult(0, 0, value, true, false);
    }

    public static EffectResult unsupported() {
        return new EffectResult(0, 0, 0, false, false);
    }
}
