package com.ragnarok.domain.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MainStats {
    private Integer hp;
    private Integer level;
    private Integer def;
    private Integer m_def;
    private Integer attack;
    private Integer magic_attack;
    private Integer aspd;
    private Integer move_speed;
    private Integer base_exp;
    private Integer job_exp;
    private Integer flee;
    private Integer hit;
    private Integer defense_rating;
    private Integer crit_shield;
    private Integer exp_ratio;
}