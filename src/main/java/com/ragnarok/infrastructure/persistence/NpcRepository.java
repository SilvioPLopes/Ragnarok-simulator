package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NpcRepository extends JpaRepository<NpcEntity, Long> {
    List<NpcEntity> findByMapName(String mapName);
    boolean existsBySeedId(String seedId);
    Optional<NpcEntity> findBySeedId(String seedId);
    Optional<NpcEntity> findFirstByMapNameAndXAndY(String mapName, int x, int y);
    Optional<NpcEntity> findFirstByMapNameAndName(String mapName, String name);

    /**
     * Encontra o NPC navi_ mais próximo dentro de um raio (Manhattan distance).
     * Usado pelo populator para linkar scripts rAthena a NPCs do cliente bRO
     * quando coordenadas não coincidem exatamente.
     */
    @Query("SELECT n FROM NpcEntity n WHERE n.mapName = :mapName " +
           "AND n.seedId LIKE 'navi_%' " +
           "AND ABS(n.x - :x) <= :radius " +
           "AND ABS(n.y - :y) <= :radius " +
           "ORDER BY ABS(n.x - :x) + ABS(n.y - :y) ASC")
    List<NpcEntity> findNaviNpcsByProximity(
            @Param("mapName") String mapName,
            @Param("x") int x,
            @Param("y") int y,
            @Param("radius") int radius);
}
