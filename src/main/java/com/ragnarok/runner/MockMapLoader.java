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

            // 1. LIMPEZA TOTAL (Ordem importa para não quebrar Foreign Keys)
            inventoryRepo.deleteAll(); // Limpa mochilas primeiro
            itemRepo.deleteAll();      // Limpa definições de itens depois

            // 2. RECRIAR ITENS (Definições)
            // ARMAS
            criarItem(itemRepo, 1L, "Katana [4]", ItemType.WEAPON, EquipSlot.HAND_2H, 60, 0, 0, 5, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 4L, "Main Gauche [4]", ItemType.WEAPON, EquipSlot.HAND_R, 43, 0, 0, 0, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 5L, "Claymore", ItemType.WEAPON, EquipSlot.HAND_2H, 180, 0, 0, 10, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 6L, "Wand", ItemType.WEAPON, EquipSlot.HAND_R, 15, 0, 30, 0, 0, 0, 5, 0, 0);

            // ARMADURAS
            criarItem(itemRepo, 2L, "Paletó", ItemType.ARMOR, EquipSlot.ARMOR, 0, 10, 0, 0, 0, 1, 0, 0, 0);
            criarItem(itemRepo, 7L, "Full Plate", ItemType.ARMOR, EquipSlot.ARMOR, 0, 45, 0, 0, 0, 0, 0, 0, 0);

            // ESCUDOS
            criarItem(itemRepo, 8L, "Guard", ItemType.ARMOR, EquipSlot.HAND_L, 0, 20, 0, 0, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 9L, "Mirror Shield", ItemType.ARMOR, EquipSlot.HAND_L, 0, 15, 0, 0, 0, 0, 0, 0, 10);

            // CAPAS E CALÇADOS
            criarItem(itemRepo, 10L, "Muffler", ItemType.ARMOR, EquipSlot.GARMENT, 0, 5, 0, 0, 2, 0, 0, 0, 0);
            criarItem(itemRepo, 11L, "Boots", ItemType.ARMOR, EquipSlot.BOOTS, 0, 4, 0, 0, 0, 0, 0, 1, 0);

            // ACESSÓRIOS
            criarItem(itemRepo, 12L, "Ring", ItemType.ARMOR, EquipSlot.ACCESSORY_RIGHT, 0, 0, 0, 2, 0, 0, 0, 0, 0);
            criarItem(itemRepo, 13L, "Glove", ItemType.ARMOR, EquipSlot.ACCESSORY_LEFT, 0, 0, 0, 0, 0, 0, 0, 2, 0);

            // CONSUMÍVEIS
            criarItem(itemRepo, 3L, "Poção Vermelha", ItemType.CONSUMABLE, EquipSlot.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            // Atualiza efeito manualmente pois o helper não tem o campo 'efeito' direto no construtor simples
            itemRepo.findById(3L).ifPresent(i -> { i.setEfeito(45); itemRepo.save(i); });

            criarItem(itemRepo, 14L, "Poção Branca", ItemType.CONSUMABLE, EquipSlot.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            itemRepo.findById(14L).ifPresent(i -> { i.setEfeito(325); itemRepo.save(i); });

            System.out.println(">>> MOCK: Itens recriados.");

            // 3. RECRIAR OU CARREGAR PLAYER
            PlayerEntity player;
            if (!playerRepo.existsById(1L)) {
                PlayerEntity p = new PlayerEntity();
                p.setName("Hero");
                p.setJobClass("Novice");
                p.setHpCurrent(50); // HP baixo para testar poção
                p.setHpMax(500);
                p.setStr(10);
                p.setAgi(10);
                p.setVit(10);
                p.setIntelligence(10);
                p.setDex(10);
                p.setLuk(10);
                player = playerRepo.save(p);
            } else {
                player = playerRepo.findById(1L).orElseThrow();
                boolean precisaSalvar = false;
                if (player.getBaseExp() == null) { player.setBaseExp(0L); precisaSalvar = true; }
                if (player.getJobExp() == null) { player.setJobExp(0L); precisaSalvar = true; }
                if (player.getStatPoints() == null) { player.setStatPoints(0); precisaSalvar = true; }
                if (player.getSkillPoints() == null) { player.setSkillPoints(0); precisaSalvar = true; }

                if (precisaSalvar) {
                    playerRepo.save(player);
                    System.out.println(">>> MOCK: Dados corrompidos do Player 1 foram corrigidos.");
                }
                player.setHpCurrent(50);
                playerRepo.save(player);
            }

            // 4. ENCHER A MOCHILA
            // Agora é seguro usar .orElseThrow() porque acabamos de criar os itens acima
            darItem(inventoryRepo, player, itemRepo.findById(1L).orElseThrow(), 1, false);  // Katana
            darItem(inventoryRepo, player, itemRepo.findById(4L).orElseThrow(), 1, false);  // Main Gauche
            darItem(inventoryRepo, player, itemRepo.findById(5L).orElseThrow(), 1, false);  // Claymore
            darItem(inventoryRepo, player, itemRepo.findById(6L).orElseThrow(), 1, false);  // Wand

            darItem(inventoryRepo, player, itemRepo.findById(2L).orElseThrow(), 1, false);  // Paletó
            darItem(inventoryRepo, player, itemRepo.findById(7L).orElseThrow(), 1, false);  // Full Plate

            darItem(inventoryRepo, player, itemRepo.findById(8L).orElseThrow(), 1, false);  // Guard
            darItem(inventoryRepo, player, itemRepo.findById(9L).orElseThrow(), 1, false);  // Mirror Shield

            darItem(inventoryRepo, player, itemRepo.findById(10L).orElseThrow(), 1, false); // Muffler
            darItem(inventoryRepo, player, itemRepo.findById(11L).orElseThrow(), 1, false); // Boots

            darItem(inventoryRepo, player, itemRepo.findById(12L).orElseThrow(), 1, false); // Ring
            darItem(inventoryRepo, player, itemRepo.findById(13L).orElseThrow(), 1, false); // Glove

            darItem(inventoryRepo, player, itemRepo.findById(3L).orElseThrow(), 20, false); // P. Vermelha
            darItem(inventoryRepo, player, itemRepo.findById(14L).orElseThrow(), 5, false); // P. Branca

            System.out.println(">>> MOCK: Inventário carregado com sucesso.");

            // 5. MAPA E MONSTROS (Legado mantido para não quebrar exploração)
            if (!mapRepo.existsById("prt_fild08")) {
                GameMapEntity map = new GameMapEntity();
                map.setId("prt_fild08");
                map.setName("Arredores de Prontera 08");
                map.setType("FIELD");
                mapRepo.save(map);

                createMonster(monsterRepo, 1002L, "Poring", 50, 10);
                createMonster(monsterRepo, 1008L, "Pupa", 427, 0);

                GameMapEntity savedMap = mapRepo.findById("prt_fild08").get();
                createSpawn(spawnRepo, monsterRepo.findById(1002L).get(), savedMap, 70);
                createSpawn(spawnRepo, monsterRepo.findById(1008L).get(), savedMap, 20);
            }
        };
    }

    private void criarItem(ItemRepository repo, Long id, String nome, ItemType type, EquipSlot slot,
                           int atk, int def, int matk,
                           int str, int agi, int vit, int intel, int dex, int mdef) {
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
        // BonusMdef ignorado por enquanto se não houver campo na entidade
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

    private void createMonster(MonsterRepository repo, Long id, String name, int hp, int atk) {
        if (!repo.existsById(id)) {
            MonsterEntity m = new MonsterEntity();
            m.setId(id);
            m.setName(name);
            m.setHp(hp);
            m.setAttack(atk);
            m.setDef(0);
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