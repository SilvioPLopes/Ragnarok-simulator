package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlayerItemRepository extends JpaRepository<PlayerItemEntity, UUID> {

    // Note que aqui é UUID, não Long
    List<PlayerItemEntity> findByPlayerId(Long playerId);

    List<PlayerItemEntity> findByPlayerIdAndEquippedTrue(Long playerId);

    java.util.List<PlayerItemEntity> findByPlayerIdAndItemId(Long playerId, Long itemId);

    void deleteByPlayerId(Long playerId);
}