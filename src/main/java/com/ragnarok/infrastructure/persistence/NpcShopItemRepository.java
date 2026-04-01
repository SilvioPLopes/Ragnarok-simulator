package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NpcShopItemRepository extends JpaRepository<NpcShopItemEntity, Long> {
    List<NpcShopItemEntity> findByNpcId(Long npcId);
    Optional<NpcShopItemEntity> findByNpcIdAndItemId(Long npcId, Long itemId);
}
