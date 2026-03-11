package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "map_monsters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MapMonsterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_id")
    private String mapId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "monster_id")
    private MonsterEntity monster;

    private Integer amount;
}