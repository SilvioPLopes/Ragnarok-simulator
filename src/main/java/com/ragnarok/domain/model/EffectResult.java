package com.ragnarok.domain.model;

public record EffectResult(
        int hpHeal,
        int spHeal,
        int damage,
        boolean supported
) {
    public static EffectResult heal(int hp, int sp) {
        return new EffectResult(hp, sp, 0, true);
    }

    public static EffectResult unsupported() {
        return new EffectResult(0, 0, 0, false);
    }
}
