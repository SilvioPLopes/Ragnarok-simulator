package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.MapPortalEntity;
import com.ragnarok.infrastructure.persistence.MapPortalRepository;
import com.ragnarok.runner.populator.dto.NaviLinkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class NaviLinkPopulator {

    private static final Logger log = LoggerFactory.getLogger(NaviLinkPopulator.class);

    @Value("${ro.assets.navi-link-lua-path}")
    private String naviLinkLuaPath;

    private final MapPortalRepository mapPortalRepository;
    private final NaviLinkLuaParser parser = new NaviLinkLuaParser();

    public NaviLinkPopulator(MapPortalRepository mapPortalRepository) {
        this.mapPortalRepository = mapPortalRepository;
    }

    public void run() throws IOException {
        log.info("NaviLinkPopulator: lendo {} ...", naviLinkLuaPath);
        List<NaviLinkData> entries = parser.parse(naviLinkLuaPath);
        log.info("NaviLinkPopulator: {} entradas lidas.", entries.size());

        int inserted = 0;
        int skipped  = 0;

        for (NaviLinkData data : entries) {
            MapPortalEntity existing = mapPortalRepository.findFirstByMapFromAndMapTo(data.mapFrom(), data.mapTo());
            if (existing != null) {
                skipped++;
                continue;
            }

            MapPortalEntity entity = new MapPortalEntity();
            entity.setMapFrom(data.mapFrom());
            entity.setXFrom(data.xFrom());
            entity.setYFrom(data.yFrom());
            entity.setMapTo(data.mapTo());
            entity.setXTo(data.xTo());
            entity.setYTo(data.yTo());
            mapPortalRepository.save(entity);
            inserted++;
        }

        log.info("NaviLinkPopulator: {} portais inseridos, {} ja existiam (skipped).", inserted, skipped);
    }
}
