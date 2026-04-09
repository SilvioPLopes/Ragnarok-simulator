package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skills")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillEntity {

    @Id
    private Long id;

    @Column(name = "aegis_name")
    private String aegisName;

    private String name;

    private String type;

    @Column(columnDefinition = "TEXT")
    private String script;

    // --- Campos de efeito ---

    @Column(name = "effect_type")
    private String effectType;       // PHYSICAL_DAMAGE, MAGICAL_DAMAGE, HEAL, BUFF, PASSIVE, UTILITY

    @Column(name = "element")
    private String element;          // NEUTRAL, FIRE, WATER, WIND, EARTH, HOLY, SHADOW, POISON, GHOST, UNDEAD

    @Column(name = "damage_formula", columnDefinition = "TEXT")
    private String damageFormula;    // ex: "ATK * skill_lv * 1.3" ou "MATK * skill_lv + INT * 5"

    @Column(name = "sp_cost")
    private Integer spCost;          // custo de SP para usar (sobreescreve valor fixo)

    @Column(name = "duration_turns")
    private Integer durationTurns;   // para buffs: quantos turnos dura

    @Column(name = "target_type")
    private String targetType;       // SELF, SINGLE, AOE

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "img_url")
    private String imgUrl;
}
