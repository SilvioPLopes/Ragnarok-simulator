package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MapMonsterRepository extends JpaRepository<MapMonsterEntity, Long> {

    @Query("SELECT m FROM MapMonsterEntity m JOIN FETCH m.monster WHERE m.mapId = :mapId")
    List<MapMonsterEntity> findByMapId(@Param("mapId") String mapId);
}