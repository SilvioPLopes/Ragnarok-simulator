package com.ragnarok.application.service;

import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock PlayerRepository playerRepository;
    @Mock JwtUtil jwtUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock FraudClient fraudClient;

    AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, playerRepository, jwtUtil, passwordEncoder, fraudClient);
    }

    @Test
    void register_success() {
        when(accountRepository.existsByUsername("joao")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        AccountEntity saved = new AccountEntity();
        saved.setId(1L);
        when(accountRepository.save(any())).thenReturn(saved);

        Long id = accountService.register("joao", "pass123", null);

        assertEquals(1L, id);
        verify(fraudClient).syncRegistrationAsync(1L, false, false, null);
    }

    @Test
    void register_duplicateUsername_throws409() {
        when(accountRepository.existsByUsername("joao")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> accountService.register("joao", "pass", null));
    }

    @Test
    void login_success() {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setPasswordHash("hashed");
        when(accountRepository.findByUsername("joao")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
        FraudClient.FraudDecision decision = FraudClient.FraudDecision.FALLBACK_APPROVED;
        when(fraudClient.checkLogin(anyLong(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(decision);
        when(jwtUtil.generateToken(1L)).thenReturn("jwt-token");

        String token = accountService.login("joao", "pass123", "127.0.0.1");

        assertEquals("jwt-token", token);
    }

    @Test
    void login_wrongPassword_throws() {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setPasswordHash("hashed");
        when(accountRepository.findByUsername("joao")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> accountService.login("joao", "wrong", "127.0.0.1"));
    }

    @Test
    void validateOwnership_success() {
        PlayerEntity player = new PlayerEntity();
        player.setId(5L);
        player.setAccountId(10L);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertDoesNotThrow(() -> accountService.validateOwnership(10L, 5L));
    }

    @Test
    void validateOwnership_wrongAccount_throws() {
        PlayerEntity player = new PlayerEntity();
        player.setId(5L);
        player.setAccountId(99L);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThrows(IllegalArgumentException.class, () -> accountService.validateOwnership(10L, 5L));
    }
}
