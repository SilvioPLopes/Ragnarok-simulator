package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.MapMonsterEntity;
import com.ragnarok.infrastructure.persistence.MapMonsterRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import com.ragnarok.runner.populator.dto.NaviMobData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Populator de validação/enriquecimento de map_monsters a partir do navi_mob_br.lua.
 *
 * Tenta fazer match de cada entrada do arquivo com MonsterEntity por aegisName (lowercase)
 * e, em seguida, por ptBrName (lowercase). Insere novos entries em map_monsters apenas
 * quando o match é confiável e o entry ainda não existe (idempotente).
 * Não modifica entries existentes.
 */
@Component
public class NaviMobPopulator {

    private static final Logger log = LoggerFactory.getLogger(NaviMobPopulator.class);

    @Value("${ro.assets.navi-mob-lua-path}")
    private String naviMobLuaPath;

    private final MonsterRepository monsterRepository;
    private final MapMonsterRepository mapMonsterRepository;
    private final NaviMobLuaParser parser = new NaviMobLuaParser();

    public NaviMobPopulator(MonsterRepository monsterRepository,
                            MapMonsterRepository mapMonsterRepository) {
        this.monsterRepository = monsterRepository;
        this.mapMonsterRepository = mapMonsterRepository;
    }

    public void run() throws IOException {
        log.info("NaviMobPopulator: lendo {} ...", naviMobLuaPath);
        List<NaviMobData> entries = parser.parse(naviMobLuaPath);
        log.info("NaviMobPopulator: {} entradas parseadas.", entries.size());

        Map<String, MonsterEntity> monsterByName = monsterRepository.findAll().stream()
                .collect(Collectors.toMap(
                        m -> m.getName().toLowerCase(),
                        m -> m,
                        (existing, duplicate) -> existing
                ));

        int inserted = 0;
        int skipped = 0;
        int notMatched = 0;

        for (NaviMobData data : entries) {
            MonsterEntity monster = monsterByName.get(data.aegisName().toLowerCase());
            if (monster == null) {
                monster = monsterByName.get(data.ptBrName().toLowerCase());
            }
            if (monster == null) {
                notMatched++;
                continue;
            }

            final long monsterId = monster.getId();
            List<MapMonsterEntity> existing = mapMonsterRepository.findByMapId(data.mapName());
            boolean alreadyExists = existing.stream()
                    .anyMatch(e -> e.getMonster().getId().equals(monsterId));

            if (alreadyExists) {
                skipped++;
                continue;
            }

            MapMonsterEntity entry = new MapMonsterEntity();
            entry.setMapId(data.mapName());
            entry.setMonster(monster);
            entry.setAmount(data.amount());
            mapMonsterRepository.save(entry);
            inserted++;
        }

        log.info("NaviMobPopulator: total={}, inseridos={}, ja existentes (skipped)={}, nao matchados={}",
                entries.size(), inserted, skipped, notMatched);
    }
}
