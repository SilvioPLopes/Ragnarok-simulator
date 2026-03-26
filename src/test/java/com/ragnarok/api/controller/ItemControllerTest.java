package com.ragnarok.api.controller;

import com.ragnarok.api.GlobalExceptionHandler;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
@Import(GlobalExceptionHandler.class)
class ItemControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ItemService itemService;

    @Test
    void getInventory_returnsList() throws Exception {
        ItemEntity item = new ItemEntity();
        item.setId(1L);
        item.setName("Red Potion");
        item.setType(ItemType.CONSUMABLE);

        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setId(UUID.randomUUID());
        pi.setItem(item);
        pi.setAmount(3);
        pi.setEquipped(false);

        when(itemService.listarInventario(1L)).thenReturn(List.of(pi));

        mvc.perform(get("/api/players/1/inventory"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Red Potion"))
           .andExpect(jsonPath("$[0].amount").value(3));
    }

    @Test
    void useItem_returnsMessage() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(itemService.usarItem(itemId)).thenReturn("Voce usou Red Potion e recuperou 45 HP.");

        mvc.perform(post("/api/players/1/inventory/" + itemId + "/use"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("Voce usou Red Potion e recuperou 45 HP."));
    }
}
