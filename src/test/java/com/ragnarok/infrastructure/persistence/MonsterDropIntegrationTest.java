package com.ragnarok.infrastructure.persistence;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.ItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=update"
})
class MonsterDropIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MonsterRepository monsterRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Fluxo Drop: Criar Monstro -> Criar Item -> Vincular Drop -> Validar Leitura")
    @Transactional
    void deveGerenciarDropsDoMonstro() {
        // --- 1. SETUP: Criar o Item (O que vai cair) ---
        ItemEntity jellopy = new ItemEntity();
        jellopy.setId(99904L); // ID de teste — não conflita com dados reais do rAthena
        jellopy.setName("Jellopy");
        jellopy.setType(ItemType.ETC);
        jellopy.setWeight(1);
        itemRepository.save(jellopy);

        // --- 2. SETUP: Criar o Monstro (Quem vai dropar) ---
        MonsterEntity poring = new MonsterEntity();
        poring.setId(99903L); // ID de teste — não conflita com dados reais do rAthena
        poring.setName("Poring");
        poring.setHp(50);

        // Importante: Salvar o monstro antes de adicionar drops (se não usar Cascade.ALL)
        poring = monsterRepository.save(poring);

        // --- 3. AÇÃO: Criar o Drop (A ligação) ---
        // Aqui verificamos se a MonsterDropEntity está bem mapeada
        MonsterDropEntity drop = new MonsterDropEntity();
        drop.setMonster(poring);
        drop.setItem(jellopy);
        drop.setRate(100.0); // 100% de chance (para teste)

        // Adiciona à lista do monstro (se a entidade tiver o método helper)
        // Se não tiver, salvamos via Cascade ou precisamos de um MonsterDropRepository
        if (poring.getDrops() == null) {
            poring.setDrops(new java.util.ArrayList<>());
        }
        poring.getDrops().add(drop);

        // Salva o Monstro (esperando que o Cascade persista o Drop)
        monsterRepository.save(poring);

        // --- 4. FLUSH (Limpar memória para testar banco real) ---
        entityManager.flush();
        entityManager.clear();

        // --- 5. VALIDAÇÃO ---
        System.out.println("🔍 Buscando Poring no banco...");
        MonsterEntity poringCarregado = monsterRepository.findById(99903L).orElseThrow();

        assertNotNull(poringCarregado.getDrops(), "A lista de drops não deve ser nula");
        assertFalse(poringCarregado.getDrops().isEmpty(), "O Poring deveria ter 1 drop");

        MonsterDropEntity dropCarregado = poringCarregado.getDrops().get(0);
        assertEquals("Jellopy", dropCarregado.getItem().getName());
        assertEquals(100.0, dropCarregado.getRate());

        System.out.println("✅ Sucesso! Poring dropa Jellopy.");
    }
}