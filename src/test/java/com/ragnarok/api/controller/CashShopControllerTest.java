package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.CashShopService;
import com.ragnarok.api.dto.request.CashBuyRequestDTO;
import com.ragnarok.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashShopController.class)
class CashShopControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CashShopService cashShopService;
    @MockBean JwtUtil jwtUtil;

    private static final String TOKEN = "Bearer test-token";

    private void stubValidToken() {
        when(jwtUtil.isValid("test-token")).thenReturn(true);
        when(jwtUtil.extractAccountId("test-token")).thenReturn(1L);
    }

    @Test
    void listItems_returns200() throws Exception {
        stubValidToken();
        when(cashShopService.listItems()).thenReturn(List.of());
        mockMvc.perform(get("/api/shop/cash/items")
                .header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void buy_returns200() throws Exception {
        stubValidToken();
        doNothing().when(cashShopService).buy(anyLong(), anyLong(), anyLong());
        mockMvc.perform(post("/api/shop/cash/buy")
                .header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CashBuyRequestDTO(10L, 1L, 1L))))
                .andExpect(status().isOk());
    }
}
