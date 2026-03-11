package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MapPortalRepository extends JpaRepository<MapPortalEntity, Long> {


    @Query("SELECT DISTINCT p.mapTo FROM MapPortalEntity p WHERE p.mapFrom = :mapFrom")
    List<String> findDestinosByMapFrom(@Param("mapFrom") String mapFrom);

    MapPortalEntity findFirstByMapFromAndMapTo(String mapFrom, String mapTo);
}