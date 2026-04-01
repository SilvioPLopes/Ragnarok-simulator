package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.GlobalExceptionHandler;
import com.ragnarok.api.dto.request.CreatePlayerRequestDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.ClassChangeService;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.security.JwtFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
@Import(GlobalExceptionHandler.class)
class PlayerControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PlayerService playerService;
    @MockitoBean AccountService accountService;
    @MockitoBean ClassChangeService classChangeService;
    @MockitoBean JwtFilter jwtFilter;

    @BeforeEach
    void passFilterThrough() throws Exception {
        doAnswer(inv -> { ((jakarta.servlet.FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void getPlayers_returnsEmptyList() throws Exception {
        when(playerService.listarPersonagens(1L)).thenReturn(List.of());
        mvc.perform(get("/api/players"))
           .andExpect(status().isOk())
           .andExpect(content().json("[]"));
    }

    @Test
    void getPlayer_notFound_returns404() throws Exception {
        when(playerService.buscarPersonagem(99L))
                .thenThrow(new IllegalArgumentException("Player not found: 99"));
        mvc.perform(get("/api/players/99"))
           .andExpect(status().isNotFound());
    }

    @Test
    void getPlayer_found_returnsPlayer() throws Exception {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(1L);
        entity.setName("Hero");
        entity.setJobClass("NOVICE");
        entity.setBaseLevel(1);
        entity.setHpCurrent(100);
        entity.setHpMax(100);
        when(playerService.buscarPersonagem(1L)).thenReturn(entity);

        mvc.perform(get("/api/players/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("Hero"));
    }

    @Test
    void createPlayer_returnsCreated() throws Exception {
        Player created = new Player();
        created.setId(1L);
        created.setName("Hero");
        when(playerService.criarNovoPersonagem(eq("Hero"), eq("NOVICE"), any())).thenReturn(created);
        mvc.perform(post("/api/players")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new CreatePlayerRequestDTO("Hero", "NOVICE"))))
           .andExpect(status().isCreated());
    }
}
