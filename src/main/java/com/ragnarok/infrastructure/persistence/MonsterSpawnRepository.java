package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * @deprecated Substituído por {@link MapMonsterRepository} que usa dados reais do rAthena.
 */
@Deprecated
@Repository
public interface MonsterSpawnRepository extends JpaRepository<MonsterSpawnEntity, Long> {
    // CORREÇÃO: JOIN FETCH carrega o monstro na mesma query, prevenindo LazyInitializationException
    @Query("SELECT s FROM MonsterSpawnEntity s JOIN FETCH s.monster WHERE s.map.id = :mapId")
    List<MonsterSpawnEntity> findByMapId(String mapId);
}