package com.ragnarok.runner;

import com.ragnarok.infrastructure.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class MockMapLoader {

    @Bean
    @Order(1)
    public CommandLineRunner loadInitialData(MonsterRepository monsterRepo,
                                             GameMapRepository mapRepo,
                                             MonsterSpawnRepository spawnRepo,
                                             PlayerRepository playerRepo) {
        return args -> {
            System.out.println(">>> MOCK: Iniciando limpeza e carga de dados...");


            PlayerEntity player;
            if (!playerRepo.existsById(1L)) {
                PlayerEntity p = new PlayerEntity();
                p.setName("Hero");
                p.setJobClass("Novice");
                p.setHpCurrent(100);
                p.setHpMax(500);
                p.setStr(10);
                p.setAgi(10);
                p.setVit(10);
                p.setIntelligence(10);
                p.setDex(10);
                p.setLuk(10);
                p.setBaseExp(0L);
                p.setJobExp(0L);
                p.setStatPoints(0);
                p.setSkillPoints(0);
                p.setMapName("prontera");
                player = playerRepo.save(p);
            } else {
                player = playerRepo.findById(1L).orElseThrow();
                if (player.getBaseExp()    == null) player.setBaseExp(0L);
                if (player.getJobExp()     == null) player.setJobExp(0L);
                if (player.getStatPoints() == null) player.setStatPoints(0);
                if (player.getSkillPoints()== null) player.setSkillPoints(0);
                playerRepo.save(player);
            }

            System.out.println(">>> MOCK: Inventário carregado.");

            if (!mapRepo.existsById("prt_fild08")) {
                GameMapEntity map = new GameMapEntity();
                map.setId("prt_fild08");
                map.setName("Arredores de Prontera 08");
                map.setType("FIELD");
                mapRepo.save(map);

                // CORRIGIDO: createMonster agora recebe baseExp e jobExp
                createMonster(monsterRepo, 1002L, "Poring", 50,  10, 50,  30);
                createMonster(monsterRepo, 1008L, "Pupa",   427,  0, 100, 60);

                GameMapEntity savedMap = mapRepo.findById("prt_fild08").get();
                createSpawn(spawnRepo, monsterRepo.findById(1002L).get(), savedMap, 70);
                createSpawn(spawnRepo, monsterRepo.findById(1008L).get(), savedMap, 20);

                System.out.println(">>> MOCK: Mapa e monstros criados.");
            }
        };
    }




    // CORRIGIDO: assinatura agora inclui baseExp e jobExp
    private void createMonster(MonsterRepository repo, Long id, String name, int hp, int atk, int baseExp, int jobExp) {
        if (!repo.existsById(id)) {
            MonsterEntity m = new MonsterEntity();
            m.setId(id);
            m.setName(name);
            m.setHp(hp);
            m.setAttack(atk);
            m.setDef(0);
            m.setBaseExp(baseExp);
            m.setJobExp(jobExp);
            repo.save(m);
        }
    }

    private void createSpawn(MonsterSpawnRepository spawnRepo, MonsterEntity m, GameMapEntity map, int amount) {
        MonsterSpawnEntity spawn = new MonsterSpawnEntity();
        spawn.setMonster(m);
        spawn.setMap(map);
        spawn.setAmount(amount);
        spawn.setRespawnTime("Instant");
        spawnRepo.save(spawn);
    }
}