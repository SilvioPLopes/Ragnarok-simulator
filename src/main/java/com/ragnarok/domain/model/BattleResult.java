package com.ragnarok.domain.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ToString
public class BattleResult {
    private String log;
    private Integer damageDealtToMonster;
    private Integer damageTakenFromMonster;
    private boolean monsterDied;
    private boolean playerDied;
    private Integer expGained;
}