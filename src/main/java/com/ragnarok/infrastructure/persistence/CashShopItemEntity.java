package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cash_shop_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CashShopItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "cash_price", nullable = false)
    private Long cashPrice;

    @Column(nullable = false)
    private boolean active = true;
}
