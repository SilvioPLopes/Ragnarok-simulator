package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "player_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemEntity item;

    private Integer amount;

    // Campos simples (sem @Column duplicado)
    private Integer refineLevel = 0;

    @Column(name = "is_equipped")
    private Boolean equipped = false;
}