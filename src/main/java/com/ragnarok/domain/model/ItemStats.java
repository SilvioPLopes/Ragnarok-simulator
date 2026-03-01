package com.ragnarok.domain.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ItemStats {
    private Integer attack;
    private Integer mAttack;
    private Integer defense;
    private Integer range;
    private Integer slots;
    private Integer levelMin;
    private Integer bonusStr;
    private Integer bonusAgi;
    private Integer bonusVit;
    private Integer bonusDex;
    private Integer bonusInt;
    private Integer bonusLuk;
    private Integer bonusHp;
    private Integer bonusSP;
    private Integer bonusFlee;
    private Integer bonusHit;
    private Integer bonusDef;
    private Integer bonusMDef;
    private Integer efeito; // Efeito como recuperar vida ou dar bonus em % sobre elemnto ou raça.
}