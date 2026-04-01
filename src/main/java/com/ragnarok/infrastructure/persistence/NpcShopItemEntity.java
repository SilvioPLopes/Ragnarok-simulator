package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "npc_shop_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NpcShopItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private NpcEntity npc;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    /** -1 means use the item's own price from the items table */
    private int price;
}
