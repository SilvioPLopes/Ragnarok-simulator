package com.ragnarok.application.service;

import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierEntity;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Retorna o modificador de dano (%) para um tipo de arma contra um tamanho de monstro.
 *
 * Tamanhos do rAthena: "Small", "Medium", "Large"
 */
@Service
public class WeaponSizeService {

    private final WeaponSizeModifierRepository repository;

    public WeaponSizeService(WeaponSizeModifierRepository repository) {
        this.repository = repository;
    }

    /**
     * @param weaponType   tipo da arma equipada (null → NONE = 100%)
     * @param monsterSize  tamanho do monstro do rAthena ("Small", "Medium", "Large")
     * @return             modificador em % (100 = normal)
     */
    @Cacheable("weaponSizeModifiers")
    public int getModifier(WeaponType weaponType, String monsterSize) {
        String key = weaponType != null ? weaponType.name() : WeaponType.NONE.name();

        WeaponSizeModifierEntity modifier = repository.findByWeaponType(key)
                .orElse(defaultModifier());

        return switch (normalizeSize(monsterSize)) {
            case "SMALL"  -> modifier.getSmallPct();
            case "LARGE"  -> modifier.getLargePct();
            default       -> modifier.getMediumPct();
        };
    }

    private String normalizeSize(String size) {
        if (size == null) return "MEDIUM";
        return size.trim().toUpperCase();
    }

    private WeaponSizeModifierEntity defaultModifier() {
        return new WeaponSizeModifierEntity(WeaponType.NONE.name(), 100, 100, 100);
    }
}
