package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_offers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TradeOfferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_player_id", nullable = false)
    private Long senderPlayerId;

    @Column(name = "receiver_player_id", nullable = false)
    private Long receiverPlayerId;

    @Column(name = "offered_player_item_id", nullable = false)
    private UUID offeredPlayerItemId;

    @Column(name = "requested_zenny", nullable = false)
    private Long requestedZenny = 0L;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
