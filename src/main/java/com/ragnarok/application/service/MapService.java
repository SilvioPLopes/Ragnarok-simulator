package com.ragnarok.application.service;

import com.ragnarok.application.dto.WalkResult;
import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);

    private final PlayerRepository playerRepository;
    private final MapPortalRepository portalRepository;
    private final MapMonsterRepository mapMonsterRepository;
    private final MonsterRepository monsterRepository;
    private final GameMapRepository gameMapRepository;
    private final Random rng = new Random();

    public MapService(PlayerRepository playerRepository,
                      MapPortalRepository portalRepository,
                      MapMonsterRepository mapMonsterRepository,
                      MonsterRepository monsterRepository,
                      GameMapRepository gameMapRepository) {
        this.playerRepository = playerRepository;
        this.portalRepository = portalRepository;
        this.mapMonsterRepository = mapMonsterRepository;
        this.monsterRepository = monsterRepository;
        this.gameMapRepository = gameMapRepository;
    }

    public String getDisplayName(String mapId) {
        return gameMapRepository.findById(mapId)
                .map(m -> m.getDisplayName() != null ? m.getDisplayName() : m.getName())
                .orElse(mapId);
    }

    public String getCurrentMap(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        return player.getMapName() != null ? player.getMapName() : "prontera";
    }

    public List<String> getPortals(String mapId) {
        return portalRepository.findDestinosByMapFrom(mapId)
                .stream()
                .filter(d -> !d.equals(mapId))
                .toList();
    }

    @Transactional
    public void travel(Long playerId, String destination) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        String currentMap = player.getMapName() != null ? player.getMapName() : "prontera";
        List<String> available = getPortals(currentMap);
        if (!available.contains(destination)) {
            throw new GameException("Portal to " + destination + " not available from " + currentMap);
        }
        player.setMapName(destination);
        playerRepository.save(player);
    }

    /**
     * Simulates walking in the current map.
     * Returns a WalkResult with encounter result.
     */
    @Transactional
    public WalkResult walk(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new GameException("Player not found: " + playerId));
        String map = player.getMapName() != null ? player.getMapName() : "prontera";

        if (rng.nextInt(100) >= 70) {
            return new WalkResult(false, null, null, null, "Nenhum monstro por aqui.");
        }

        List<MapMonsterEntity> entries = mapMonsterRepository.findByMapId(map);
        if (entries.isEmpty()) {
            return new WalkResult(false, null, null, null, "Nenhum monstro registrado neste mapa.");
        }

        int totalWeight = entries.stream().mapToInt(e -> e.getAmount() != null ? e.getAmount() : 1).sum();
        int roll = rng.nextInt(totalWeight);
        int accumulated = 0;
        MapMonsterEntity chosen = entries.get(0);
        for (MapMonsterEntity entry : entries) {
            accumulated += entry.getAmount() != null ? entry.getAmount() : 1;
            if (roll < accumulated) { chosen = entry; break; }
        }

        MonsterEntity monster = chosen.getMonster();
        if (monster.getHp() == null || monster.getHp() <= 0) {
            monster.setHp(100);
            monsterRepository.save(monster);
        }

        return new WalkResult(true, monster.getId(), monster.getName(), monster.getHp(),
                monster.getName().toUpperCase() + " APARECEU!");
    }
}
