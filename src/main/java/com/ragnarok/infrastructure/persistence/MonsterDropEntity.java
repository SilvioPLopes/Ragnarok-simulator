package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "monster_drops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MonsterDropEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id")
    private MonsterEntity monster;

    @ManyToOne(fetch = FetchType.EAGER) // Eager para carregar o nome do item junto
    @JoinColumn(name = "item_id")
    private ItemEntity item;

    private Double rate; // Chance de drop (ex: 0.03)

    // Construtor auxiliar
    public MonsterDropEntity(MonsterEntity monster, ItemEntity item, Double rate) {
        this.monster = monster;
        this.item = item;
        this.rate = rate;
    }
}