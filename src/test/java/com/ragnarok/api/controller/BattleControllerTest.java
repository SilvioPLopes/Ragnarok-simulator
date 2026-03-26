package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.GlobalExceptionHandler;
import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.BattleService;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.infrastructure.security.JwtFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BattleController.class)
@Import(GlobalExceptionHandler.class)
class BattleControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean BattleService battleService;
    @MockBean AccountService accountService;
    @MockBean JwtFilter jwtFilter;

    @BeforeEach
    void passFilterThrough() throws Exception {
        doAnswer(inv -> { ((jakarta.servlet.FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void attack_returnsMessage() throws Exception {
        when(battleService.realizarAtaque(1L, 2L)).thenReturn("ATAQUE: causou 45 de dano.");

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("ATAQUE: causou 45 de dano."));
    }

    @Test
    void attack_playerDead_returns400() throws Exception {
        when(battleService.realizarAtaque(1L, 2L)).thenThrow(new PlayerDeadException());

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void attack_playerNotFound_returns404() throws Exception {
        when(battleService.realizarAtaque(99L, 2L))
                .thenThrow(new IllegalArgumentException("Player not found"));

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(99L, 2L))))
           .andExpect(status().isNotFound());
    }
}
