package com.ragnarok.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ElementalDamage {
    private Integer neutral;
    private Integer poison;
    private Integer earth;
    private Integer shadow;
    private Integer water;
    private Integer undead;
    private Integer fire;
    private Integer holy;
    private Integer wind;
    private Integer ghost;
}