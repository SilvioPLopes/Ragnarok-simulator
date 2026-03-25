package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.api.dto.request.LoginRequestDTO;
import com.ragnarok.api.dto.request.RegisterRequestDTO;
import com.ragnarok.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountService accountService;
    @MockBean JwtUtil jwtUtil;

    @Test
    void register_returns201() throws Exception {
        when(accountService.register("joao", "pass", null)).thenReturn(1L);

        mockMvc.perform(post("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequestDTO("joao", "pass", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(1));
    }

    @Test
    void register_duplicate_returns409() throws Exception {
        when(accountService.register(any(), any(), any())).thenThrow(new IllegalStateException("Username ja existe"));

        mockMvc.perform(post("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequestDTO("joao", "pass", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void login_returns200WithToken() throws Exception {
        when(accountService.login(eq("joao"), eq("pass"), any())).thenReturn("jwt-token");

        mockMvc.perform(post("/api/accounts/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequestDTO("joao", "pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_wrongCredentials_returns404() throws Exception {
        when(accountService.login(any(), any(), any())).thenThrow(new IllegalArgumentException("Credenciais invalidas"));

        mockMvc.perform(post("/api/accounts/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequestDTO("joao", "wrong"))))
                .andExpect(status().isNotFound());
    }
}
