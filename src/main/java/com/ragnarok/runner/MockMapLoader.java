package com.ragnarok.runner;

import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.ItemType;
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
                                             PlayerRepository playerRepo,
                                             ItemRepository itemRepo,
                                             PlayerItemRepository inventoryRepo) {
        return args -> {
            System.out.println(">>> MOCK: Iniciando limpeza e carga de dados...");

            inventoryRepo.deleteAll();
            itemRepo.deleteAll();

            // ARMAS
            criarItem(itemRepo, 1L,  "Katana [4]",     ItemType.WEAPON, EquipSlot.HAND_2H, 60,  0,  0, 5, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 4L,  "Main Gauche [4]", ItemType.WEAPON, EquipSlot.HAND_R,  43,  0,  0, 0, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 5L,  "Claymore",        ItemType.WEAPON, EquipSlot.HAND_2H, 180, 0,  0, 10,0, 0, 0, 0, 0);
            criarItem(itemRepo, 6L,  "Wand",            ItemType.WEAPON, EquipSlot.HAND_R,  15,  0, 30, 0, 0, 0, 5, 0, 0);
            // ARMADURAS
            criarItem(itemRepo, 2L,  "Paletó",          ItemType.ARMOR,  EquipSlot.ARMOR,    0, 10,  0, 0, 0, 1, 0, 0, 0);
            criarItem(itemRepo, 7L,  "Full Plate",      ItemType.ARMOR,  EquipSlot.ARMOR,    0, 45,  0, 0, 0, 0, 0, 0, 0);
            // ESCUDOS
            criarItem(itemRepo, 8L,  "Guard",           ItemType.ARMOR,  EquipSlot.HAND_L,   0, 20,  0, 0, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 9L,  "Mirror Shield",   ItemType.ARMOR,  EquipSlot.HAND_L,   0, 15,  0, 0, 0, 0, 0, 0,10);
            // CAPAS E CALÇADOS
            criarItem(itemRepo, 10L, "Muffler",         ItemType.ARMOR,  EquipSlot.GARMENT,  0,  5,  0, 0, 2, 0, 0, 0, 0);
            criarItem(itemRepo, 11L, "Boots",           ItemType.ARMOR,  EquipSlot.BOOTS,    0,  4,  0, 0, 0, 0, 0, 1, 0);
            // ACESSÓRIOS
            criarItem(itemRepo, 12L, "Ring",            ItemType.ARMOR,  EquipSlot.ACCESSORY_RIGHT, 0, 0, 0, 2, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 13L, "Glove",           ItemType.ARMOR,  EquipSlot.ACCESSORY_LEFT,  0, 0, 0, 0, 0, 0, 0, 2, 0);
            // CONSUMÍVEIS
            criarItem(itemRepo, 3L,  "Poção Vermelha",  ItemType.CONSUMABLE, EquipSlot.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            itemRepo.findById(3L).ifPresent(i -> { i.setEfeito(45);  itemRepo.save(i); });
            criarItem(itemRepo, 14L, "Poção Branca",    ItemType.CONSUMABLE, EquipSlot.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            itemRepo.findById(14L).ifPresent(i -> { i.setEfeito(325); itemRepo.save(i); });

            System.out.println(">>> MOCK: Itens recriados.");

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
                player = playerRepo.save(p);
            } else {
                player = playerRepo.findById(1L).orElseThrow();
                if (player.getBaseExp()    == null) player.setBaseExp(0L);
                if (player.getJobExp()     == null) player.setJobExp(0L);
                if (player.getStatPoints() == null) player.setStatPoints(0);
                if (player.getSkillPoints()== null) player.setSkillPoints(0);
                playerRepo.save(player);
            }

            darItem(inventoryRepo, player, itemRepo.findById(1L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(4L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(5L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(6L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(2L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(7L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(8L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(9L).orElseThrow(),  1, false);
            darItem(inventoryRepo, player, itemRepo.findById(10L).orElseThrow(), 1, false);
            darItem(inventoryRepo, player, itemRepo.findById(11L).orElseThrow(), 1, false);
            darItem(inventoryRepo, player, itemRepo.findById(12L).orElseThrow(), 1, false);
            darItem(inventoryRepo, player, itemRepo.findById(13L).orElseThrow(), 1, false);
            darItem(inventoryRepo, player, itemRepo.findById(3L).orElseThrow(),  20, false);
            darItem(inventoryRepo, player, itemRepo.findById(14L).orElseThrow(), 5,  false);

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

    private void criarItem(ItemRepository repo, Long id, String nome, ItemType type, EquipSlot slot,
                           int atk, int def, int matk, int str, int agi, int vit, int intel, int dex, int mdef) {
        ItemEntity item = new ItemEntity();
        item.setId(id);
        item.setName(nome);
        item.setType(type);
        item.setEquipSlot(slot);
        item.setAttack(atk);
        item.setDefense(def);
        item.setMagicAttack(matk);
        item.setBonusStr(str);
        item.setBonusAgi(agi);
        item.setBonusVit(vit);
        item.setBonusInt(intel);
        item.setBonusDex(dex);
        repo.save(item);
    }

    private void darItem(PlayerItemRepository repo, PlayerEntity p, ItemEntity item, int qtd, boolean equipado) {
        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setPlayer(p);
        pi.setItem(item);
        pi.setAmount(qtd);
        pi.setEquipped(equipado);
        pi.setRefineLevel(0);
        repo.save(pi);
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