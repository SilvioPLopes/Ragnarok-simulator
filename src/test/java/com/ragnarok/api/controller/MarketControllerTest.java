package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.MarketService;
import com.ragnarok.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketController.class)
class MarketControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MarketService marketService;
    @MockBean JwtUtil jwtUtil;

    private static final String TOKEN = "Bearer test-token";

    private void stubValidToken() {
        when(jwtUtil.isValid("test-token")).thenReturn(true);
        when(jwtUtil.extractAccountId("test-token")).thenReturn(1L);
    }

    @Test
    void list_returns200() throws Exception {
        stubValidToken();
        when(marketService.listActive(null)).thenReturn(List.of());
        mockMvc.perform(get("/api/market/listings")
                .header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void listWithFilter_returns200() throws Exception {
        stubValidToken();
        when(marketService.listActive(500L)).thenReturn(List.of());
        mockMvc.perform(get("/api/market/listings?itemId=500")
                .header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }
}
