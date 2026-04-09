package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.GameMapEntity;
import com.ragnarok.infrastructure.persistence.GameMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
public class NaviMapPopulator {

    private static final Logger log = LoggerFactory.getLogger(NaviMapPopulator.class);

    @Value("${ro.assets.navi-map-lua-path}")
    private String naviMapLuaPath;

    private final GameMapRepository gameMapRepository;
    private final NaviMapLuaParser parser = new NaviMapLuaParser();

    public NaviMapPopulator(GameMapRepository gameMapRepository) {
        this.gameMapRepository = gameMapRepository;
    }

    public void run() throws IOException {
        log.info("NaviMapPopulator: lendo {} ...", naviMapLuaPath);
        Map<String, String> mapIdToDisplayName = parser.parse(naviMapLuaPath);
        log.info("NaviMapPopulator: {} entradas lidas.", mapIdToDisplayName.size());

        int updated = 0;
        int notFound = 0;

        for (Map.Entry<String, String> entry : mapIdToDisplayName.entrySet()) {
            String mapId = entry.getKey();
            String displayName = entry.getValue();

            Optional<GameMapEntity> opt = gameMapRepository.findById(mapId);
            if (opt.isEmpty()) {
                notFound++;
                continue;
            }

            GameMapEntity entity = opt.get();
            entity.setDisplayName(displayName);
            gameMapRepository.save(entity);
            updated++;
        }

        log.info("NaviMapPopulator: {} mapas atualizados, {} nao encontrados no banco.", updated, notFound);
    }
}
