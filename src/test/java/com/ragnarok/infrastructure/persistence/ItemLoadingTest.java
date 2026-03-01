package com.ragnarok.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.domain.model.ItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemLoadingTest {

    @Test
    @DisplayName("Deve carregar itens do JSON e mapear corretamente para ItemEntity")
    void deveCarregarItensDoJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        // Lê o arquivo items.json da pasta de testes
        List<ItemEntity> itens = mapper.readValue(
                new ClassPathResource("items.json").getFile(),
                new TypeReference<>() {}
        );

        // Validações
        assertEquals(4, itens.size(), "Deve carregar 3 itens");

        // Valida Ahlspiess (Arma)
        ItemEntity ahlspiess = itens.stream().filter(i -> i.getId() == 1478L).findFirst().orElseThrow();
        assertEquals("Ahlspiess", ahlspiess.getName());
        assertEquals(ItemType.WEAPON, ahlspiess.getType());
        assertEquals(120, ahlspiess.getAttack(), "O ataque deve ser 120");
        assertEquals(0, ahlspiess.getSlots());

        // Valida Plate Armor (Defesa)
        ItemEntity armor = itens.stream().filter(i -> i.getId() == 2309L).findFirst().orElseThrow();
        assertEquals(45, armor.getDefense(), "A defesa deve ser 45");

        System.out.println("Sucesso: JSON lido e convertido para Entidades!");
    }
}