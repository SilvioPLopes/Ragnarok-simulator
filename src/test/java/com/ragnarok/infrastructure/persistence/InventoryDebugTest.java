package com.ragnarok.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre",
        "spring.jpa.hibernate.ddl-auto=create-drop", // Garante banco limpo e correto
        "spring.jpa.show-sql=true"
})
class InventoryDebugTest {

    @Autowired private PlayerRepository playerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PlayerItemRepository playerItemRepository;

    // --- TESTE 1: Validar apenas o Player (Sem itens) ---
    @Test
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
    @DisplayName("Teste 2: Catálogo de Itens (Valida Carga JSON)")
    void testeItemCatalogoIsolado() {
        System.out.println(">>> INICIO TESTE 2: Catalogo <<<");

        // Verifica se a carga inicial funcionou para o ID da Ahlspiess
        Long idBuscado = 1478L;
        boolean existe = itemRepository.existsById(idBuscado);

        System.out.println("Item " + idBuscado + " existe no banco? " + existe);

        // Se este falhar, o problema está no ItemDataInitializer ou no items.json
        assertTrue(existe, "O banco deveria ter o item 1478 carregado via JSON");
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

        // 2. Pega Item do Catálogo
        ItemEntity item = itemRepository.findById(1478L)
                .orElseThrow(() -> new IllegalStateException("Item 1478 não encontrado para o teste 3"));

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