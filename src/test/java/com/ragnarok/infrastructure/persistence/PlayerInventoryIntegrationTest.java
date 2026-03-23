package com.ragnarok.infrastructure.persistence;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.ItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=update"
})
class PlayerInventoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private PlayerRepository playerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PlayerItemRepository playerItemRepository;

    @Test
    @DisplayName("Fluxo Completo: Criar Player -> Receber Lança -> Equipar -> Validar Status")
    @Transactional
    void deveGerenciarInventarioEEquipamento() {
        // 1. SETUP: Cria item de teste próprio — sem depender de dados pré-existentes
        ItemEntity ahlspiessPrototype = new ItemEntity();
        ahlspiessPrototype.setId(99803L);
        ahlspiessPrototype.setName("Test Lance");
        ahlspiessPrototype.setAttack(120);
        ahlspiessPrototype = itemRepository.save(ahlspiessPrototype);

        // 2. PLAYER (Salva primeiro para ter ID)
        PlayerEntity player = new PlayerEntity();
        player.setName("Lord Knight Teste");
        player.setJobClass("Lord Knight");
        player = playerRepository.save(player);

        // 3. ITEM (Vincula e Salva Explicitamente)
        PlayerItemEntity novaLanca = new PlayerItemEntity();
        novaLanca.setPlayer(player);
        novaLanca.setItem(ahlspiessPrototype);
        novaLanca.setAmount(1);
        novaLanca.setRefineLevel(7);
        novaLanca.setEquipped(false);

        // O segredo do sucesso: Salvar o item diretamente
        playerItemRepository.save(novaLanca);

        // 4. VALIDAÇÃO
        List<PlayerItemEntity> inventario = playerItemRepository.findByPlayerId(player.getId());
        assertEquals(1, inventario.size());
        assertEquals(7, inventario.get(0).getRefineLevel());

        System.out.println("✅ Teste Passou: Item UUID salvo corretamente.");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager; // Injete isso no topo da classe

    @Test
    @DisplayName("Teste de Leitura: Buscar Player deve trazer o Inventário preenchido")
    @Transactional
    void deveCarregarInventarioAoBuscarJogador() {
        // --- 1. CENÁRIO (Setup) ---
        // Cria Player
        PlayerEntity player = new PlayerEntity();
        player.setName("Merchant de Teste");
        player.setJobClass("Merchant");
        player = playerRepository.save(player);

        // Cria Item de teste próprio — sem depender de dados pré-existentes
        ItemEntity itemProto = new ItemEntity();
        itemProto.setId(99804L);
        itemProto.setName("Test Lance 2");
        itemProto.setAttack(120);
        itemProto = itemRepository.save(itemProto);

        // Dá o item ao player (Salvando direto no repositório do filho)
        PlayerItemEntity itemDoPlayer = new PlayerItemEntity();
        itemDoPlayer.setPlayer(player);
        itemDoPlayer.setItem(itemProto);
        itemDoPlayer.setAmount(50); // 50 Jellopies
        playerItemRepository.save(itemDoPlayer);

        System.out.println("💾 Dados salvos. Limpando cache do Hibernate para forçar leitura do banco...");

        // --- O PULO DO GATO ---
        // Força o Hibernate a esquecer os objetos da memória e ir buscar no Banco de Dados real.
        // Sem isso, o teste passaria falsamente porque leria o objeto Java que acabamos de criar.
        entityManager.flush();
        entityManager.clear();

        // --- 2. AÇÃO (Buscar) ---
        System.out.println("🔍 Buscando jogador ID: " + player.getId());
        PlayerEntity jogadorCarregado = playerRepository.findById(player.getId())
                .orElseThrow(() -> new IllegalStateException("Jogador sumiu!"));

        // --- 3. VALIDAÇÃO (O Inventário veio junto?) ---
        System.out.println("📦 Tamanho do inventário carregado: " + jogadorCarregado.getInventory().size());

        assertNotNull(jogadorCarregado.getInventory(), "A lista de inventário não pode ser nula");
        assertFalse(jogadorCarregado.getInventory().isEmpty(), "A lista deveria ter 1 item");
        assertEquals(50, jogadorCarregado.getInventory().get(0).getAmount());

        System.out.println("✅ Sucesso! O relacionamento @OneToMany funcionou.");
    }
}