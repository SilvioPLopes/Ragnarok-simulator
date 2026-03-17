package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class MonsterCatalogServiceTest {

    @Autowired
    private MonsterCatalogService monsterCatalogService;

    @Autowired
    private MonsterRepository monsterRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private GameMapRepository gameMapRepository; // Injeção Nova

    @Test
    @DisplayName("Deve baixar o Escorpião, salvar Drops e criar Mapas de Spawn (moc_fild08)")
    @Transactional
    void deveCarregarESalvarMonstroComDropsEMapas() {
        // 1. Executa a ação
        Long monsterId = 1001L;
        monsterCatalogService.carregarESalvarMonstro(monsterId);

        // 2. Valida Monstro
        Optional<MonsterEntity> monstro = monsterRepository.findById(monsterId);
        assertTrue(monstro.isPresent());

        // 3. Valida Drops (Lógica antiga)
        assertTrue(itemRepository.existsById(990L), "Item Red Blood deve existir");

        // 4. VALIDAÇÃO NOVA: MAPAS
        // O JSON diz que o Escorpião nasce em "moc_fild08". O sistema deve ter criado esse mapa.
        boolean mapaExiste = gameMapRepository.existsById("moc_fild08");
        assertTrue(mapaExiste, "O mapa moc_fild08 deveria ter sido criado automaticamente");

        // 5. VALIDAÇÃO NOVA: SPAWN
        // Verifica se o monstro tem um spawn vinculado a esse mapa
        assertFalse(monstro.get().getSpawns().isEmpty(), "A lista de spawns não pode estar vazia");

        boolean temSpawnNoDeserto = monstro.get().getSpawns().stream()
                .anyMatch(spawn -> spawn.getMap().getId().equals("moc_fild08"));

        assertTrue(temSpawnNoDeserto, "Deveria ter criado o vínculo de spawn no mapa moc_fild08");

        System.out.println("SUCESSO! Monstro, Drop (Red Blood) e Mapa (moc_fild08) validados.");
    }
}