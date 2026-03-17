package com.ragnarok.application.service;

import com.ragnarok.domain.model.Item;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class ItemServiceTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("Deve salvar uma Katana e verificar se o Ataque (Stats) foi achatado na tabela")
    void deveSalvarItem() {
        // 1. Ação
        Long idKatana = 4001L;
        itemService.criarItemDeTeste(idKatana, "Katana", 60);

        // 2. Validação no Banco
        Optional<ItemEntity> banco = itemRepository.findById(idKatana);

        assertTrue(banco.isPresent(), "A Katana deveria estar no banco");
        assertEquals("Katana", banco.get().getName());

        // Teste do Flattening: O ataque estava dentro de 'stats' no domínio,
        // mas deve estar na coluna direta 'attack' no banco.
        assertEquals(60, banco.get().getAttack(), "O Ataque deve estar na coluna plana");
        assertEquals(2, banco.get().getSlots(), "Os Slots devem estar na coluna plana");

        System.out.println("SUCESSO! Item salvo: " + banco.get().getName());
    }
}