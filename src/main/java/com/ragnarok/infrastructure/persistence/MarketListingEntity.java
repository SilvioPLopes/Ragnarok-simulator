package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_listings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MarketListingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_player_id", nullable = false)
    private Long sellerPlayerId;

    @Column(name = "player_item_id", nullable = false)
    private UUID playerItemId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "price_zenny", nullable = false)
    private Long priceZenny;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "listed_at")
    private Instant listedAt = Instant.now();

    @Column(name = "sold_at")
    private Instant soldAt;
}
