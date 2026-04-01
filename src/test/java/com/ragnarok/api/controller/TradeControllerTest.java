package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.TradeService;
import com.ragnarok.api.dto.request.*;
import com.ragnarok.infrastructure.persistence.TradeOfferEntity;
import com.ragnarok.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TradeService tradeService;
    @MockBean JwtUtil jwtUtil;

    private static final String TOKEN = "Bearer test-token";

    private void stubValidToken() {
        when(jwtUtil.isValid("test-token")).thenReturn(true);
        when(jwtUtil.extractAccountId("test-token")).thenReturn(1L);
    }

    @Test
    void createOffer_returns201() throws Exception {
        stubValidToken();
        TradeOfferEntity saved = new TradeOfferEntity();
        saved.setId(1L); saved.setSenderPlayerId(1L); saved.setReceiverPlayerId(2L);
        saved.setOfferedPlayerItemId(UUID.randomUUID()); saved.setRequestedZenny(10L); saved.setStatus("PENDING");
        when(tradeService.createOffer(any(), any(), any(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/trade/offers")
                .header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateTradeOfferRequestDTO(1L, 2L, UUID.randomUUID(), 10L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listReceived_returns200() throws Exception {
        stubValidToken();
        when(tradeService.listReceived(2L)).thenReturn(List.of());
        mockMvc.perform(get("/api/trade/offers/received/2")
                .header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void acceptOffer_returns200() throws Exception {
        stubValidToken();
        doNothing().when(tradeService).acceptOffer(1L, 2L);
        mockMvc.perform(post("/api/trade/offers/1/accept")
                .header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TradeActionRequestDTO(2L))))
                .andExpect(status().isOk());
    }
}
