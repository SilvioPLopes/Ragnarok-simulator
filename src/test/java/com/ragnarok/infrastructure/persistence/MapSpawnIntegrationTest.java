package com.ragnarok.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MapSpawnIntegrationTest {

    @Autowired private GameMapRepository mapRepository;
    @Autowired private MonsterRepository monsterRepository;
    @Autowired private MonsterSpawnRepository spawnRepository;

    @Test
    @DisplayName("Spawn: Deve localizar monstros vinculados a um mapa específico")
    @Transactional
    void deveEncontrarSpawnsPorMapa() {
        // 1. SETUP: Mapa Isolado (Evita conflito com MockMapLoader que usa prt_fild08)
        GameMapEntity map = new GameMapEntity();
        map.setId("test_map_001"); // ID Único para o teste
        map.setName("Test Field");
        map.setType("FIELD");
        map = mapRepository.save(map);

        // 2. SETUP: Monstro (Poring)
        MonsterEntity monster = new MonsterEntity();
        monster.setId(9999L); // ID alto para evitar colisão
        monster.setName("Test Poring");
        monster.setHp(50);
        monster.setAttack(10);
        monster.setDef(0);
        monster = monsterRepository.save(monster);

        // 3. SETUP: Spawn (Vínculo)
        MonsterSpawnEntity spawn = new MonsterSpawnEntity();
        spawn.setMap(map);
        spawn.setMonster(monster);
        spawn.setAmount(50);
        spawn.setRespawnTime("Instant");
        spawnRepository.save(spawn);

        // 4. EXECUÇÃO: Busca pelo ID do Mapa de Teste
        List<MonsterSpawnEntity> resultados = spawnRepository.findByMapId("test_map_001");

        // 5. VALIDAÇÃO
        assertFalse(resultados.isEmpty(), "ERRO: A lista de spawns não deveria estar vazia.");
        assertEquals(1, resultados.size(), "ERRO: Deveria haver apenas 1 spawn neste mapa de teste.");

        MonsterSpawnEntity resultado = resultados.get(0);
        assertEquals("Test Poring", resultado.getMonster().getName());
        assertEquals("test_map_001", resultado.getMap().getId());
        assertEquals(50, resultado.getAmount());
    }
}