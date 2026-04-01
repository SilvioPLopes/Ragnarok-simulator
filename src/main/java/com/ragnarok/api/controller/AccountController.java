package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.LoginRequestDTO;
import com.ragnarok.api.dto.request.RegisterRequestDTO;
import com.ragnarok.api.dto.response.LoginResponseDTO;
import com.ragnarok.application.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> register(@RequestBody RegisterRequestDTO dto) {
        Long id = accountService.register(dto.username(), dto.password(), dto.email());
        return Map.of("accountId", id);
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        AccountService.LoginResult result = accountService.login(dto.username(), dto.password(), ip);
        return new LoginResponseDTO(result.token(), result.accountId());
    }
}
