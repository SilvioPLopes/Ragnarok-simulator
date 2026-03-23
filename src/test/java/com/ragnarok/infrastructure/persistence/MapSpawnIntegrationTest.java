package com.ragnarok.infrastructure.persistence;

import com.ragnarok.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapSpawnIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MonsterRepository monsterRepository;
    @Autowired private MapMonsterRepository mapMonsterRepository;

    @Test
    @DisplayName("MapMonster: deve localizar monstros vinculados a um mapa específico")
    @Transactional
    void deveEncontrarMapMonstersPorMapa() {
        // 1. SETUP: Monstro
        MonsterEntity monster = new MonsterEntity();
        monster.setId(9999L);
        monster.setName("Test Poring");
        monster.setHp(50);
        monster.setAttack(10);
        monster.setDef(0);
        monster = monsterRepository.save(monster);

        // 2. SETUP: Vínculo monstro → mapa
        MapMonsterEntity mapMonster = new MapMonsterEntity();
        mapMonster.setMapId("test_map_001");
        mapMonster.setMonster(monster);
        mapMonster.setAmount(50);
        mapMonsterRepository.save(mapMonster);

        // 3. EXECUÇÃO
        List<MapMonsterEntity> resultados = mapMonsterRepository.findByMapId("test_map_001");

        // 4. VALIDAÇÃO
        assertFalse(resultados.isEmpty(), "A lista de map_monsters não deveria estar vazia.");
        assertEquals(1, resultados.size(), "Deveria haver apenas 1 entrada para este mapa.");

        MapMonsterEntity resultado = resultados.get(0);
        assertEquals("Test Poring", resultado.getMonster().getName());
        assertEquals("test_map_001", resultado.getMapId());
        assertEquals(50, resultado.getAmount());
    }
}
