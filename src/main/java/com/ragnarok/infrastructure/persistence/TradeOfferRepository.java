package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeOfferRepository extends JpaRepository<TradeOfferEntity, Long> {
    List<TradeOfferEntity> findByReceiverPlayerIdAndStatus(Long receiverPlayerId, String status);
    List<TradeOfferEntity> findBySenderPlayerId(Long senderPlayerId);
}
