package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

/**
 * Modificadores de dano físico por tipo de arma × tamanho do monstro.
 * Small / Medium / Large em porcentagem (100 = normal, 75 = -25%, 50 = -50%).
 */
@Entity
@Table(name = "weapon_size_modifiers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WeaponSizeModifierEntity {

    @Id
    @Column(name = "weapon_type", length = 50)
    private String weaponType;   // matches WeaponType enum name

    @Column(name = "small_pct", nullable = false)
    private int smallPct;

    @Column(name = "medium_pct", nullable = false)
    private int mediumPct;

    @Column(name = "large_pct", nullable = false)
    private int largePct;
}
