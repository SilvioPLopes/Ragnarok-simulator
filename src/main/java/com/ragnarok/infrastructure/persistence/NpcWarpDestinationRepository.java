package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NpcWarpDestinationRepository extends JpaRepository<NpcWarpDestinationEntity, Long> {
    List<NpcWarpDestinationEntity> findByNpcId(Long npcId);
    Optional<NpcWarpDestinationEntity> findByNpcIdAndMapName(Long npcId, String mapName);
}
