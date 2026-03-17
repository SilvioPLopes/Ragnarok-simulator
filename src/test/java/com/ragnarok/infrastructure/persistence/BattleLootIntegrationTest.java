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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=update"
})
class BattleLootIntegrationTest {

    @Autowired private PlayerRepository playerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private MonsterRepository monsterRepository;
    @Autowired private PlayerItemRepository playerItemRepository;
    @Autowired private BattleService battleService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Loot: Ao matar monstro, item com 100% de chance deve aparecer no inventário")
    @Transactional
    void deveDroparItemAoMatarMonstro() {
        // --- 1. SETUP: JOGADOR FORTE ---
        System.out.println("1. Criando Player...");
        PlayerEntity player = new PlayerEntity();
        player.setName("Loot Hunter");
        player.setJobClass("Merchant");
        player.setStr(99); // Hit Kill
        player = playerRepository.save(player);

        // --- 2. SETUP: ITEM RARO (O DROP) ---
        System.out.println("2. Criando Item de Drop (Carta Poring)...");
        ItemEntity card = new ItemEntity();
        card.setId(99901L); // ID de teste — não conflita com dados reais do rAthena
        card.setName("Poring Card");
        card.setType(ItemType.CARD);
        card.setWeight(1);
        card = itemRepository.save(card); // Capture o retorno!

        // --- 3. SETUP: MONSTRO COM DROP CONFIGURADO ---
        System.out.println("3. Criando Monstro com Drop...");
        MonsterEntity poring = new MonsterEntity();
        poring.setId(99902L); // ID de teste — não conflita com dados reais do rAthena
        poring.setName("Rich Poring");
        poring.setHp(10); // Pouco HP para morrer rápido
        poring.setDef(0);

        // Adiciona o Drop na lista do Monstro (Taxa 100.0 = 100%)
        poring.addDrop(card, 100.0);

        poring = monsterRepository.save(poring);

        // --- 4. FLUSH (Para garantir que o Mapper vai ler do banco) ---
        entityManager.flush();
        entityManager.clear();

        // --- 5. AÇÃO: ATAQUE FATAL ---
        System.out.println("4. Executando Ataque Fatal...");
        // O dano do player (Str 99 * 2 = 198) deve ser maior que o HP (10)
        String log = battleService.realizarAtaque(player.getId(), poring.getId());

        System.out.println("LOG: " + log);

        // --- 6. VALIDAÇÕES ---

        // A. Verifica se o monstro morreu no banco
        MonsterEntity monstroMorto = monsterRepository.findById(poring.getId()).orElseThrow();
        assertEquals(0, monstroMorto.getHp(), "O monstro deveria estar com 0 HP.");

        // B. Verifica se o Log informou a vitória e o drop
        assertTrue(log.contains("VITÓRIA") || log.contains("VITORIA"), "Log deve confirmar a vitória.");
        assertTrue(log.contains("Poring Card"), "Log deve confirmar o drop.");

        // C. VERIFICAÇÃO FINAL: O Item está no inventário do Player?
        List<PlayerItemEntity> inventario = playerItemRepository.findByPlayerId(player.getId());

        assertFalse(inventario.isEmpty(), "O inventário não deveria estar vazio.");
        assertEquals("Poring Card", inventario.get(0).getItem().getName(), "O item no inventário deve ser a Carta Poring.");
    }
}