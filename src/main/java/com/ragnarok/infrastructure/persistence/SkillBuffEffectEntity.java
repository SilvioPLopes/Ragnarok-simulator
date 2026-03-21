package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skill_buff_effects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillBuffEffectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "stat_type", nullable = false)
    private String statType;         // STR, AGI, VIT, INT, DEX, LUK, FLEE, DEF, M_DEF, MAX_HP_PERCENT, ATK_PERCENT

    @Column(name = "value_formula", nullable = false)
    private String valueFormula;     // ex: "skill_lv * 3"
}
