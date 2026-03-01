package com.ragnarok.domain.model;

import jakarta.persistence.*;
import lombok.*;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ItemDropInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String monsterName;
    private String rate;
    private String highestSpawn; // Texto descritivo ex: "70 at Sograt Desert"
    private String element;
    private String flee; // String pois vem "--" as vezes
    private String hit;
}