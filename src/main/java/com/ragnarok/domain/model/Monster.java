package com.ragnarok.domain.model;

import lombok.*;
import java.util.List;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Monster {


    private Long id;
    private String mongoId;
    private String name;
    private String size;
    private String race;
    private String type;
    private Integer elementPower;
    private String gifUrl;
    private Integer baseExp;
    private Integer jobExp;

    private MainAttributes attributes;
    private MainStats stats;
    private ElementalDamage elementalDamage;


    private List<MonsterDrop> drops;
    private List<String> modes;
}

