package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PlayerRepository playerRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final FraudClient fraudClient;

    public AccountService(AccountRepository accountRepository,
                          PlayerRepository playerRepository,
                          JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          FraudClient fraudClient) {
        this.accountRepository = accountRepository;
        this.playerRepository = playerRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.fraudClient = fraudClient;
    }

    @Transactional
    public Long register(String username, String password, String email) {
        if (accountRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username ja existe: " + username);
        }
        AccountEntity account = new AccountEntity();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setEmail(email);
        AccountEntity saved = accountRepository.save(account);
        fraudClient.syncRegistrationAsync(saved.getId(), false, false, null);
        return saved.getId();
    }

    public record LoginResult(String token, Long accountId) {}

    public LoginResult login(String username, String password, String ipAddress) {
        AccountEntity account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new GameException("Credenciais invalidas"));
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new GameException("Credenciais invalidas");
        }
        FraudClient.FraudDecision decision = fraudClient.checkLogin(
                account.getId(), ipAddress, "BR", account.isEmailVerified(), account.isAgeVerified());
        if (decision.isBlocked()) {
            throw new GameException("Acesso bloqueado pelo sistema antifraude");
        }
        String token = jwtUtil.generateToken(account.getId());
        return new LoginResult(token, account.getId());
    }

    public void validateOwnership(Long accountId, Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player nao encontrado: " + playerId));
        if (!accountId.equals(player.getAccountId())) {
            throw new IllegalArgumentException("Player nao pertence a esta conta");
        }
    }
}
