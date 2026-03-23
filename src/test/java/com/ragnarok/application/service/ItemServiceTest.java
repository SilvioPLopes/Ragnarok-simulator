package com.ragnarok.application.service;

import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.runner.RagnarokTerminalRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class ItemServiceTest {

    @MockBean
    @SuppressWarnings("unused")
    private RagnarokTerminalRunner ragnarokTerminalRunner;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @Transactional
    @DisplayName("Deve persistir item com campos flat (flattening — attack/slots em colunas diretas)")
    void deveSalvarItemComFlattening() {
        ItemEntity entity = new ItemEntity();
        entity.setId(4001L);
        entity.setName("Katana");
        entity.setAttack(60);
        entity.setDefense(0);
        entity.setSlots(2);
        entity.setWeight(10);
        entity.setPrice(100);
        entity.setType(ItemType.WEAPON);
        entity.setEquipSlot(EquipSlot.HAND_R);
        itemRepository.save(entity);

        Optional<ItemEntity> banco = itemRepository.findById(4001L);

        assertTrue(banco.isPresent(), "A Katana deveria estar no banco");
        assertEquals("Katana", banco.get().getName());
        // Flattening: campos aninhados do domínio persistidos em colunas diretas
        assertEquals(60, banco.get().getAttack(), "O Ataque deve estar na coluna plana");
        assertEquals(2, banco.get().getSlots(), "Os Slots devem estar na coluna plana");
    }
}