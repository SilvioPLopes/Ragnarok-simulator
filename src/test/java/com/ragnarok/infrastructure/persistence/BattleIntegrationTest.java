package com.ragnarok.infrastructure.persistence;

import com.ragnarok.application.service.BattleService;
import com.ragnarok.domain.model.ItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre",
        "spring.jpa.hibernate.ddl-auto=update" // Recria o banco limpo para o teste
})
class BattleIntegrationTest {

    @Autowired private PlayerRepository playerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PlayerItemRepository playerItemRepository;
    @Autowired private MonsterRepository monsterRepository;
    @Autowired private BattleService battleService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Batalha: Deve calcular Dano Físico (Status + Arma) corretamente via Banco de Dados")
    @Transactional
    void deveExecutarBatalhaCompleta() {
        // =================================================================================
        // 1. SETUP: O JOGADOR (Lord Knight)
        // =================================================================================
        System.out.println("1. Criando Jogador...");
        PlayerEntity player = new PlayerEntity();
        player.setName("Siegfried");
        player.setJobClass("Lord Knight");
        player.setHpCurrent(1000);
        player.setVit(10);
        player.setStr(50);
        player.setDex(20);
        player.setAgi(1);
        player.setIntelligence(1);
        player.setLuk(10);

        // CORREÇÃO 1: Atualize a referência do player
        player = playerRepository.save(player);

        // =================================================================================
        // 2. SETUP: A ARMA (Balmung)
        // =================================================================================
        System.out.println("2. Criando Arma Lendária...");
        ItemEntity sword = new ItemEntity();
        sword.setId(5000L); // ID Manual ativa o comportamento de "Merge"
        sword.setName("Balmung");
        sword.setType(ItemType.WEAPON);
        sword.setAttack(250);
        sword.setSlots(0);

        // CORREÇÃO CRÍTICA 2: Capture o objeto retornado!
        // A variável 'sword' agora aponta para o objeto OFICIAL do banco.
        sword = itemRepository.save(sword);

        // =================================================================================
        // 3. SETUP: O INIMIGO (Poring de Pedra)
        // =================================================================================
        System.out.println("3. Criando Monstro...");
        MonsterEntity monster = new MonsterEntity();
        monster.setId(1002L);
        monster.setName("Stone Poring");
        monster.setHp(1000);
        monster.setDef(10);
        monster.setAttack(100);

        // CORREÇÃO 3: Atualize a referência do monstro
        monster = monsterRepository.save(monster);

        // =================================================================================
        // 4. AÇÃO: EQUIPAR A ARMA
        // =================================================================================
        System.out.println("4. Equipando Jogador...");
        PlayerItemEntity inventoryItem = new PlayerItemEntity();
        inventoryItem.setPlayer(player); // Usa o player gerenciado
        inventoryItem.setItem(sword);    // Usa a sword gerenciada (CRÍTICO)
        inventoryItem.setAmount(1);
        inventoryItem.setRefineLevel(0);
        inventoryItem.setEquipped(true);

        playerItemRepository.save(inventoryItem);

        // =================================================================================
        // 5. FLUSH: LIMPAR A MEMÓRIA
        // =================================================================================
        entityManager.flush();
        entityManager.clear();

        // =================================================================================
        // 6. EXECUÇÃO: A BATALHA
        // =================================================================================
        System.out.println("5. Executando Ataque...");
        String battleLog = battleService.realizarAtaque(player.getId(), monster.getId());

        System.out.println(">>> RESULTADO: " + battleLog);

        // =================================================================================
        // 7. VALIDAÇÃO MATEMÁTICA
        // =================================================================================
        // Cálculo Esperado:
        // (+) StatusATK = STR(50) * 2 = 100
        // (+) WeaponATK = 250
        // (-) MonsterDEF = 10
        // -----------------------
        // TOTAL DANO = 340

        assertNotNull(battleLog);

        // Verifica se identificou a arma correta (Teste do PlayerMapper + ItemMapper)
        assertTrue(battleLog.contains("Balmung"),
                "ERRO: O sistema não identificou a arma equipada. Verifique o PlayerMapper.");

        // Verifica o Dano Matemático (Teste da BattleEngine)
        assertTrue(battleLog.contains("causou 340 de dano"),
                "ERRO: O dano calculado está incorreto. Esperado: 340. Log: " + battleLog);
    }
    @Test
    @DisplayName("Batalha: Deve processar a morte do Jogador em um contra-ataque letal")
    @Transactional
    void deveExecutarMorteDoJogador() {
        PlayerEntity player = new PlayerEntity();
        player.setName("Novice");
        player.setHpCurrent(50);
        player.setVit(5);
        // CORREÇÃO: Injeção de status mínimos para blindar a matemática
        player.setStr(1);
        player.setDex(1);
        player.setAgi(1);
        player.setIntelligence(1);
        player.setLuk(1);
        player = playerRepository.save(player);

        MonsterEntity monster = new MonsterEntity();
        monster.setId(1003L);
        monster.setName("Baphomet");
        monster.setHp(10000);
        monster.setAttack(500);
        monster = monsterRepository.save(monster);

        String battleLog = battleService.realizarAtaque(player.getId(), monster.getId());

        assertTrue(battleLog.startsWith("FATAL:"), "ERRO: O fluxo não interrompeu na morte do jogador.");
        assertTrue(battleLog.contains("você morreu"), "ERRO: Mensagem de Game Over ausente no log.");
    }
}