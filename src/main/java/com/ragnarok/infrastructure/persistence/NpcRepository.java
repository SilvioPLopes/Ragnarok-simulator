package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NpcRepository extends JpaRepository<NpcEntity, Long> {
    List<NpcEntity> findByMapName(String mapName);
    boolean existsBySeedId(String seedId);
    Optional<NpcEntity> findBySeedId(String seedId);
}
