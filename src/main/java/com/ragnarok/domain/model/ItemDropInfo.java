package com.ragnarok.domain.model;

import jakarta.persistence.*;
import lombok.*;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ItemDropInfo {

    private Long id;
    private String monsterName;
    private String rate;
    private String highestSpawn;
    private String element;
    private String flee;
    private String hit;
}