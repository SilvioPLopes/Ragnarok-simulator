package com.ragnarok.infrastructure.persistence;

import com.ragnarok.runner.RagnarokTerminalRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.show-sql=true"
})
class InventoryDebugTest {

    @MockitoBean
    @SuppressWarnings("unused")
    private RagnarokTerminalRunner ragnarokTerminalRunner;

    @Autowired private PlayerRepository playerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PlayerItemRepository playerItemRepository;

    // --- TESTE 1: Validar apenas o Player (Sem itens) ---
    @Test
    @Transactional
    @DisplayName("Teste 1: Salvar Player isolado (Usa Setters para evitar erro de construtor)")
    void testePlayerIsolado() {
        System.out.println(">>> INICIO TESTE 1: Player <<<");

        // CORREÇÃO: Usando construtor vazio + Setters
        PlayerEntity p = new PlayerEntity();
        p.setName("Teste Player");
        p.setJobClass("Novice");
        // Campos obrigatórios mínimos (ajuste conforme sua entidade se houver @NotNull)
        p.setBaseLevel(1);
        p.setJobLevel(1);

        PlayerEntity salvo = playerRepository.save(p);

        System.out.println("Player salvo com ID: " + salvo.getId());
        assertNotNull(salvo.getId(), "O ID do player não pode ser nulo");
        System.out.println(">>> FIM TESTE 1: Sucesso <<<");
    }

    // --- TESTE 2: Validar apenas o Catálogo (Sem player) ---
    @Test
    @DisplayName("Teste 2: Catálogo de Itens (Cria e valida item próprio de teste)")
    @Transactional
    void testeItemCatalogoIsolado() {
        System.out.println(">>> INICIO TESTE 2: Catalogo <<<");

        // Cria um item de teste próprio — sem depender de dados pré-existentes
        ItemEntity item = new ItemEntity();
        item.setId(99801L);
        item.setName("Test Lance");
        item.setAttack(120);
        itemRepository.save(item);

        Long idBuscado = 99801L;
        boolean existe = itemRepository.existsById(idBuscado);

        System.out.println("Item " + idBuscado + " existe no banco? " + existe);

        assertTrue(existe, "O item de teste deveria estar salvo no banco");
        System.out.println(">>> FIM TESTE 2: Sucesso <<<");
    }

    // --- TESTE 3: O Teste Crítico (Relacionamento UUID) ---
    @Test
    @DisplayName("Teste 3: Salvar Item no Inventário (Valida UUID)")
    @Transactional
    void testePlayerItemUUID() {
        System.out.println(">>> INICIO TESTE 3: UUID Inventory <<<");

        // 1. Cria Player (com Setters)
        PlayerEntity player = new PlayerEntity();
        player.setName("Dono do Item");
        player.setJobClass("Mage");
        player = playerRepository.save(player);

        // 2. Cria Item de teste próprio — sem depender de dados pré-existentes
        ItemEntity item = new ItemEntity();
        item.setId(99802L);
        item.setName("Test Ahlspiess");
        item.setAttack(120);
        item = itemRepository.save(item);

        // 3. Monta o Objeto de Inventário
        PlayerItemEntity inventoryItem = new PlayerItemEntity();
        inventoryItem.setPlayer(player);
        inventoryItem.setItem(item);
        inventoryItem.setAmount(1);
        inventoryItem.setRefineLevel(0);
        inventoryItem.setEquipped(true);

        System.out.println("Tentando salvar PlayerItemEntity...");

        // AQUI É ONDE O ERRO DO UUID VS BIGINT ACONTECIA
        PlayerItemEntity salvo = playerItemRepository.save(inventoryItem);

        System.out.println("Objeto Salvo!");
        System.out.println("ID Gerado: " + salvo.getId());

        assertNotNull(salvo.getId());
        // Se o ID for Long, este teste vai falhar com ClassCastException ou similar
        assertTrue(salvo.getId() instanceof UUID, "O ID deveria ser um UUID");

        System.out.println(">>> FIM TESTE 3: Sucesso <<<");
    }
}