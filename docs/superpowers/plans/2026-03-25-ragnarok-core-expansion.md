20# ragnarok-core Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar Login/JWT, Troca entre jogadores, Loja NPC+Cash e Mercado no ragnarok-core em duas ondas de agentes paralelos.

**Architecture:** Onda 1 despacha A1 (JWT), A2 (Trade), A3 (Shop) em paralelo — sem dependências entre si. Onda 2 despacha B1 (Market) e B2 (JWT Wiring) após Onda 1 completa. Todos seguem o padrão Entity→Repository→Service→Controller do projeto. Sem Spring Security — auth via `OncePerRequestFilter` customizado.

**Tech Stack:** Java 17, Spring Boot 3.4.2, jjwt 0.12.6, spring-security-crypto (BCrypt), PostgreSQL, JaCoCo (≥85% line / ≥62% branch).

---

## Referências rápidas

- Spec: `docs/superpowers/specs/2026-03-25-ragnarok-core-expansion-design.md`
- Pacote base: `com.ragnarok`
- Entities: `com.ragnarok.infrastructure.persistence`
- Services: `com.ragnarok.application.service`
- Controllers: `com.ragnarok.api.controller`
- Security: `com.ragnarok.infrastructure.security`
- Config: `com.ragnarok.infrastructure.config`
- `FraudClient` já existe: `com.ragnarok.infrastructure.antifraude.FraudClient`
- `GameException` já existe: `com.ragnarok.domain.exception.GameException`
- `PlayerRepository` já existe: `com.ragnarok.infrastructure.persistence.PlayerRepository`
- `PlayerItemRepository` já existe: `com.ragnarok.infrastructure.persistence.PlayerItemRepository`
- `PlayerItemEntity` já existe: `com.ragnarok.infrastructure.persistence.PlayerItemEntity`
- Testes em: `src/test/java/com/ragnarok/`
- Comando de teste: `./mvnw test -Djacoco.skip=true -Dtest=NomeDoTeste -q`
- Comando completo: `./mvnw test -q`

---

## ONDA 1 — Agentes A1, A2, A3 em paralelo

> Cada agente trabalha em arquivos distintos. Nenhuma dependência entre eles durante Onda 1.
> A3 depende de `AccountRepository` do A1 — use `@Autowired(required = false)` durante dev e garanta que A1 terminou antes de rodar testes de A3 que envolvam cashPoints.

---

## Agente A1 — Login / JWT

### A1-Task 1: Dependências no pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Adicionar dependências**

Dentro de `<dependencies>` em `pom.xml`, adicionar após o bloco existente:

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<!-- BCrypt sem Spring Security completo -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

- [ ] **Step 2: Verificar compilação**

```bash
./mvnw compile -q
```
Esperado: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build(auth): add jjwt and spring-security-crypto dependencies"
```

---

### A1-Task 2: AccountEntity + AccountRepository

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/AccountEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/AccountRepository.java`
- Modify: `src/main/java/com/ragnarok/infrastructure/persistence/PlayerEntity.java`

- [ ] **Step 1: Criar AccountEntity**

```java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String email;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "age_verified")
    private boolean ageVerified = false;

    @Column(name = "cash_points")
    private Long cashPoints = 0L;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 2: Criar AccountRepository**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

- [ ] **Step 3: Adicionar accountId em PlayerEntity**

Em `PlayerEntity.java`, adicionar após o campo `activeBuffsJson`:

```java
@Column(name = "account_id")
private Long accountId;
```

- [ ] **Step 4: Verificar compilação**

```bash
./mvnw compile -q
```
Esperado: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/persistence/AccountEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/AccountRepository.java \
        src/main/java/com/ragnarok/infrastructure/persistence/PlayerEntity.java
git commit -m "feat(auth): add AccountEntity, AccountRepository, accountId on PlayerEntity"
```

---

### A1-Task 3: JwtUtil

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/security/JwtUtil.java`
- Create: `src/test/java/com/ragnarok/infrastructure/security/JwtUtilTest.java`

- [ ] **Step 1: Escrever teste falhando**

```java
package com.ragnarok.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-that-is-at-least-256-bits-long-for-hs256");
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken(42L);
        assertTrue(jwtUtil.isValid(token));
        assertEquals(42L, jwtUtil.extractAccountId(token));
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertFalse(jwtUtil.isValid("token.invalido.aqui"));
    }

    @Test
    void expiredTokenReturnsFalse() {
        JwtUtil shortLived = new JwtUtil("test-secret-key-that-is-at-least-256-bits-long-for-hs256", -1L);
        String token = shortLived.generateToken(1L);
        assertFalse(shortLived.isValid(token));
    }
}
```

- [ ] **Step 2: Rodar teste para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=JwtUtilTest -q
```
Esperado: FAIL — `JwtUtil` não existe

- [ ] **Step 3: Implementar JwtUtil**

```java
package com.ragnarok.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret) {
        this(secret, 86_400_000L); // 24h default
    }

    JwtUtil(String secret, long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long accountId) {
        return Jwts.builder()
                .subject(accountId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Long extractAccountId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 4: Adicionar `app.jwt.secret` em application.properties**

```properties
app.jwt.secret=ragnarok-secret-key-change-in-production-must-be-at-least-256-bits
```

- [ ] **Step 5: Rodar teste**

```bash
./mvnw test -Djacoco.skip=true -Dtest=JwtUtilTest -q
```
Esperado: `Tests run: 3, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/security/JwtUtil.java \
        src/test/java/com/ragnarok/infrastructure/security/JwtUtilTest.java \
        src/main/resources/application.properties
git commit -m "feat(auth): add JwtUtil with HS256 sign/verify"
```

---

### A1-Task 4: BCryptPasswordEncoder bean + AppConfig

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/config/AppConfig.java`

- [ ] **Step 1: Criar AppConfig**

```java
package com.ragnarok.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 2: Verificar compilação**

```bash
./mvnw compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/config/AppConfig.java
git commit -m "feat(auth): add PasswordEncoder bean (BCrypt)"
```

---

### A1-Task 5: AccountService

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/AccountService.java`
- Create: `src/test/java/com/ragnarok/application/service/AccountServiceTest.java`

- [ ] **Step 1: Escrever testes falhando**

```java
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
```

- [ ] **Step 2: Rodar teste para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=AccountServiceTest -q
```
Esperado: FAIL — `AccountService` não existe

- [ ] **Step 3: Implementar AccountService**

```java
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

    public String login(String username, String password, String ipAddress) {
        AccountEntity account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Credenciais invalidas"));
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciais invalidas");
        }
        FraudClient.FraudDecision decision = fraudClient.checkLogin(
                account.getId(), ipAddress, "BR", account.isEmailVerified(), account.isAgeVerified());
        if (decision.isBlocked()) {
            throw new GameException("Acesso bloqueado pelo sistema antifraude");
        }
        return jwtUtil.generateToken(account.getId());
    }

    public void validateOwnership(Long accountId, Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player nao encontrado: " + playerId));
        if (!accountId.equals(player.getAccountId())) {
            throw new IllegalArgumentException("Player nao pertence a esta conta");
        }
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=AccountServiceTest -q
```
Esperado: `Tests run: 6, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/AccountService.java \
        src/test/java/com/ragnarok/application/service/AccountServiceTest.java
git commit -m "feat(auth): add AccountService with register, login, validateOwnership"
```

---

### A1-Task 6: JwtFilter

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/security/JwtFilter.java`
- Create: `src/test/java/com/ragnarok/infrastructure/security/JwtFilterTest.java`

- [ ] **Step 1: Escrever testes falhando**

```java
package com.ragnarok.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock JwtUtil jwtUtil;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;
    @Mock PrintWriter writer;

    JwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtFilter(jwtUtil);
    }

    @Test
    void publicRoute_noToken_passes() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/accounts/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void validToken_setsAccountId() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/players/1");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractAccountId("valid-token")).thenReturn(42L);

        filter.doFilterInternal(request, response, chain);

        verify(request).setAttribute("accountId", 42L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingToken_returns401() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/players/1");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void invalidToken_returns401() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/players/1");
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtUtil.isValid("bad-token")).thenReturn(false);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=JwtFilterTest -q
```
Esperado: FAIL

- [ ] **Step 3: Implementar JwtFilter**

```java
package com.ragnarok.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/accounts/register",
            "/api/accounts/login"
    );

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (PUBLIC_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendUnauthorized(response);
            return;
        }
        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            sendUnauthorized(response);
            return;
        }
        request.setAttribute("accountId", jwtUtil.extractAccountId(token));
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=JwtFilterTest -q
```
Esperado: `Tests run: 4, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/security/JwtFilter.java \
        src/test/java/com/ragnarok/infrastructure/security/JwtFilterTest.java
git commit -m "feat(auth): add JwtFilter with public route bypass"
```

---

### A1-Task 7: AccountController + DTOs + GlobalExceptionHandler (patch)

**Files:**
- Create: `src/main/java/com/ragnarok/api/dto/request/RegisterRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/request/LoginRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/response/LoginResponseDTO.java`
- Create: `src/main/java/com/ragnarok/api/controller/AccountController.java`
- Modify: `src/main/java/com/ragnarok/api/GlobalExceptionHandler.java`
- Create: `src/test/java/com/ragnarok/api/controller/AccountControllerTest.java`

- [ ] **Step 1: Criar DTOs**

```java
// RegisterRequestDTO.java
package com.ragnarok.api.dto.request;
public record RegisterRequestDTO(String username, String password, String email) {}

// LoginRequestDTO.java
package com.ragnarok.api.dto.request;
public record LoginRequestDTO(String username, String password) {}

// LoginResponseDTO.java
package com.ragnarok.api.dto.response;
public record LoginResponseDTO(String token, Long accountId) {}
```

- [ ] **Step 2: Adicionar handler 409 em GlobalExceptionHandler**

No `GlobalExceptionHandler.java` existente, adicionar método:

```java
@ExceptionHandler(IllegalStateException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public Map<String, String> handleConflict(IllegalStateException ex) {
    return Map.of("error", ex.getMessage());
}
```

- [ ] **Step 3: Escrever teste falhando**

```java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.api.dto.request.LoginRequestDTO;
import com.ragnarok.api.dto.request.RegisterRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
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
```

- [ ] **Step 4: Rodar para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=AccountControllerTest -q
```

- [ ] **Step 5: Criar AccountController**

```java
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
        String token = accountService.login(dto.username(), dto.password(), ip);
        return new LoginResponseDTO(token, null); // accountId omitido por segurança
    }
}
```

- [ ] **Step 6: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=AccountControllerTest -q
```
Esperado: `Tests run: 4, Failures: 0`

- [ ] **Step 7: Rodar todos os testes de A1**

```bash
./mvnw test -Djacoco.skip=true -Dtest="JwtUtilTest,JwtFilterTest,AccountServiceTest,AccountControllerTest" -q
```
Esperado: todos passando

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/ragnarok/api/ \
        src/test/java/com/ragnarok/api/controller/AccountControllerTest.java \
        src/main/java/com/ragnarok/api/GlobalExceptionHandler.java
git commit -m "feat(auth): add AccountController, DTOs, patch GlobalExceptionHandler 409"
```

---

## Agente A2 — Troca entre jogadores

### A2-Task 1: TradeOfferEntity + TradeOfferRepository

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/TradeOfferEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/TradeOfferRepository.java`

- [ ] **Step 1: Criar TradeOfferEntity**

```java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_offers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TradeOfferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_player_id", nullable = false)
    private Long senderPlayerId;

    @Column(name = "receiver_player_id", nullable = false)
    private Long receiverPlayerId;

    @Column(name = "offered_player_item_id", nullable = false)
    private UUID offeredPlayerItemId;

    @Column(name = "requested_zenny", nullable = false)
    private Long requestedZenny = 0L;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING / ACCEPTED / REJECTED / CANCELLED

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 2: Criar TradeOfferRepository**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeOfferRepository extends JpaRepository<TradeOfferEntity, Long> {
    List<TradeOfferEntity> findByReceiverPlayerIdAndStatus(Long receiverPlayerId, String status);
    List<TradeOfferEntity> findBySenderPlayerId(Long senderPlayerId);
}
```

- [ ] **Step 3: Verificar compilação**

```bash
./mvnw compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/persistence/TradeOfferEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/TradeOfferRepository.java
git commit -m "feat(trade): add TradeOfferEntity and TradeOfferRepository"
```

---

### A2-Task 2: TradeService

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/TradeService.java`
- Create: `src/test/java/com/ragnarok/application/service/TradeServiceTest.java`

- [ ] **Step 1: Escrever testes falhando**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock TradeOfferRepository tradeOfferRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock FraudClient fraudClient;

    TradeService tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeService(tradeOfferRepository, playerRepository, playerItemRepository, fraudClient);
    }

    @Test
    void createOffer_success() {
        PlayerEntity sender = player(1L, 100L);
        PlayerEntity receiver = player(2L, 50L);
        PlayerItemEntity item = playerItem(sender);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(playerItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(tradeOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> tradeService.createOffer(1L, 2L, item.getId(), 10L));
    }

    @Test
    void acceptOffer_success() {
        UUID itemId = UUID.randomUUID();
        TradeOfferEntity offer = offer(1L, 2L, itemId, 20L);
        PlayerEntity sender = player(1L, 100L);
        PlayerEntity receiver = player(2L, 50L);
        PlayerItemEntity item = playerItem(itemId, sender, 1001L);

        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(playerItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(fraudClient.checkItemTrade(anyLong(), anyLong(), anyString(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);

        tradeService.acceptOffer(offer.getId(), 2L);

        assertEquals(receiver, item.getPlayer());
        assertEquals(80L, receiver.getZenny()); // 50 - 20 + nada; sender recebe 20
        assertEquals("ACCEPTED", offer.getStatus());
    }

    @Test
    void acceptOffer_senderLostItem_throws() {
        UUID itemId = UUID.randomUUID();
        TradeOfferEntity offer = offer(1L, 2L, itemId, 0L);
        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(playerItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> tradeService.acceptOffer(offer.getId(), 2L));
    }

    @Test
    void acceptOffer_insufficientZenny_throws() {
        UUID itemId = UUID.randomUUID();
        TradeOfferEntity offer = offer(1L, 2L, itemId, 100L);
        PlayerEntity sender = player(1L, 500L);
        PlayerEntity receiver = player(2L, 50L); // tem 50, precisa de 100
        PlayerItemEntity item = playerItem(itemId, sender, 1001L);

        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(playerItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class, () -> tradeService.acceptOffer(offer.getId(), 2L));
    }

    @Test
    void rejectOffer_success() {
        TradeOfferEntity offer = offer(1L, 2L, UUID.randomUUID(), 0L);
        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        tradeService.rejectOffer(offer.getId(), 2L);

        assertEquals("REJECTED", offer.getStatus());
    }

    @Test
    void cancelOffer_byNonSender_throws() {
        TradeOfferEntity offer = offer(1L, 2L, UUID.randomUUID(), 0L);
        when(tradeOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThrows(IllegalArgumentException.class, () -> tradeService.cancelOffer(offer.getId(), 99L));
    }

    // helpers
    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity(); p.setId(id); p.setZenny(zenny); return p;
    }
    private PlayerItemEntity playerItem(PlayerEntity owner) {
        return playerItem(UUID.randomUUID(), owner, 1001L);
    }
    private PlayerItemEntity playerItem(UUID id, PlayerEntity owner, Long itemId) {
        PlayerItemEntity pi = new PlayerItemEntity(); pi.setId(id); pi.setPlayer(owner);
        ItemEntity item = new ItemEntity(); item.setId(itemId); pi.setItem(item); pi.setAmount(1);
        return pi;
    }
    private TradeOfferEntity offer(Long senderId, Long receiverId, UUID itemId, Long zenny) {
        TradeOfferEntity o = new TradeOfferEntity();
        o.setId(1L); o.setSenderPlayerId(senderId); o.setReceiverPlayerId(receiverId);
        o.setOfferedPlayerItemId(itemId); o.setRequestedZenny(zenny); o.setStatus("PENDING");
        return o;
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=TradeServiceTest -q
```

- [ ] **Step 3: Implementar TradeService**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TradeService {

    private final TradeOfferRepository tradeOfferRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final FraudClient fraudClient;

    public TradeService(TradeOfferRepository tradeOfferRepository,
                        PlayerRepository playerRepository,
                        PlayerItemRepository playerItemRepository,
                        FraudClient fraudClient) {
        this.tradeOfferRepository = tradeOfferRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.fraudClient = fraudClient;
    }

    @Transactional
    public TradeOfferEntity createOffer(Long senderPlayerId, Long receiverPlayerId,
                                        UUID playerItemId, Long requestedZenny) {
        playerRepository.findById(senderPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("Sender nao encontrado"));
        playerRepository.findById(receiverPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("Receiver nao encontrado"));
        playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado"));

        TradeOfferEntity offer = new TradeOfferEntity();
        offer.setSenderPlayerId(senderPlayerId);
        offer.setReceiverPlayerId(receiverPlayerId);
        offer.setOfferedPlayerItemId(playerItemId);
        offer.setRequestedZenny(requestedZenny != null ? requestedZenny : 0L);
        return tradeOfferRepository.save(offer);
    }

    @Transactional
    public void acceptOffer(Long offerId, Long receiverPlayerId) {
        TradeOfferEntity offer = loadPendingOffer(offerId);
        if (!offer.getReceiverPlayerId().equals(receiverPlayerId)) {
            throw new IllegalArgumentException("Voce nao e o destinatario desta oferta");
        }
        PlayerItemEntity item = playerItemRepository.findById(offer.getOfferedPlayerItemId())
                .orElseThrow(() -> new IllegalStateException("Item nao esta mais no inventario do remetente"));

        PlayerEntity sender = playerRepository.findById(offer.getSenderPlayerId()).orElseThrow();
        PlayerEntity receiver = playerRepository.findById(receiverPlayerId).orElseThrow();

        if (receiver.getZenny() < offer.getRequestedZenny()) {
            throw new IllegalStateException("Zenny insuficiente para aceitar a troca");
        }

        FraudClient.FraudDecision decision = fraudClient.checkItemTrade(
                offer.getSenderPlayerId(),
                item.getItem().getId(),
                offer.getOfferedPlayerItemId().toString(),
                offer.getRequestedZenny());
        if (decision.isBlocked()) {
            throw new GameException("Troca bloqueada pelo sistema antifraude");
        }

        item.setPlayer(receiver);
        receiver.setZenny(receiver.getZenny() - offer.getRequestedZenny());
        sender.setZenny(sender.getZenny() + offer.getRequestedZenny());
        offer.setStatus("ACCEPTED");

        playerItemRepository.save(item);
        playerRepository.save(sender);
        playerRepository.save(receiver);
        tradeOfferRepository.save(offer);
    }

    @Transactional
    public void rejectOffer(Long offerId, Long receiverPlayerId) {
        TradeOfferEntity offer = loadPendingOffer(offerId);
        if (!offer.getReceiverPlayerId().equals(receiverPlayerId)) {
            throw new IllegalArgumentException("Voce nao e o destinatario desta oferta");
        }
        offer.setStatus("REJECTED");
        tradeOfferRepository.save(offer);
    }

    @Transactional
    public void cancelOffer(Long offerId, Long senderPlayerId) {
        TradeOfferEntity offer = loadPendingOffer(offerId);
        if (!offer.getSenderPlayerId().equals(senderPlayerId)) {
            throw new IllegalArgumentException("Apenas o remetente pode cancelar a oferta");
        }
        offer.setStatus("CANCELLED");
        tradeOfferRepository.save(offer);
    }

    public List<TradeOfferEntity> listReceived(Long playerId) {
        return tradeOfferRepository.findByReceiverPlayerIdAndStatus(playerId, "PENDING");
    }

    public List<TradeOfferEntity> listSent(Long playerId) {
        return tradeOfferRepository.findBySenderPlayerId(playerId);
    }

    private TradeOfferEntity loadPendingOffer(Long offerId) {
        TradeOfferEntity offer = tradeOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta nao encontrada: " + offerId));
        if (!"PENDING".equals(offer.getStatus())) {
            throw new IllegalStateException("Oferta nao esta mais pendente: " + offer.getStatus());
        }
        return offer;
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=TradeServiceTest -q
```
Esperado: `Tests run: 6, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/TradeService.java \
        src/test/java/com/ragnarok/application/service/TradeServiceTest.java
git commit -m "feat(trade): add TradeService with offer create/accept/reject/cancel"
```

---

### A2-Task 3: TradeController + DTOs

**Files:**
- Create: `src/main/java/com/ragnarok/api/dto/request/CreateTradeOfferRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/request/TradeActionRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/response/TradeOfferResponseDTO.java`
- Create: `src/main/java/com/ragnarok/api/controller/TradeController.java`
- Create: `src/test/java/com/ragnarok/api/controller/TradeControllerTest.java`

- [ ] **Step 1: Criar DTOs**

```java
// CreateTradeOfferRequestDTO.java
package com.ragnarok.api.dto.request;
import java.util.UUID;
public record CreateTradeOfferRequestDTO(Long senderPlayerId, Long receiverPlayerId,
                                          UUID playerItemId, Long requestedZenny) {}

// TradeActionRequestDTO.java
package com.ragnarok.api.dto.request;
public record TradeActionRequestDTO(Long playerId) {}

// TradeOfferResponseDTO.java
package com.ragnarok.api.dto.response;
import com.ragnarok.infrastructure.persistence.TradeOfferEntity;
import java.util.UUID;
public record TradeOfferResponseDTO(Long id, Long senderPlayerId, Long receiverPlayerId,
                                     UUID offeredPlayerItemId, Long requestedZenny, String status) {
    public static TradeOfferResponseDTO from(TradeOfferEntity e) {
        return new TradeOfferResponseDTO(e.getId(), e.getSenderPlayerId(), e.getReceiverPlayerId(),
                e.getOfferedPlayerItemId(), e.getRequestedZenny(), e.getStatus());
    }
}
```

- [ ] **Step 2: Escrever teste falhando**

```java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.TradeService;
import com.ragnarok.api.dto.request.*;
import com.ragnarok.infrastructure.persistence.TradeOfferEntity;
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

    @Test
    void createOffer_returns201() throws Exception {
        TradeOfferEntity saved = new TradeOfferEntity();
        saved.setId(1L); saved.setSenderPlayerId(1L); saved.setReceiverPlayerId(2L);
        saved.setOfferedPlayerItemId(UUID.randomUUID()); saved.setRequestedZenny(10L); saved.setStatus("PENDING");
        when(tradeService.createOffer(any(), any(), any(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/trade/offers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateTradeOfferRequestDTO(1L, 2L, UUID.randomUUID(), 10L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listReceived_returns200() throws Exception {
        when(tradeService.listReceived(2L)).thenReturn(List.of());
        mockMvc.perform(get("/api/trade/offers/received/2"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptOffer_returns200() throws Exception {
        doNothing().when(tradeService).acceptOffer(1L, 2L);
        mockMvc.perform(post("/api/trade/offers/1/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TradeActionRequestDTO(2L))))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: Rodar para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=TradeControllerTest -q
```

- [ ] **Step 4: Criar TradeController**

```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.TradeOfferResponseDTO;
import com.ragnarok.application.service.TradeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trade/offers")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradeOfferResponseDTO create(@RequestBody CreateTradeOfferRequestDTO dto) {
        return TradeOfferResponseDTO.from(
                tradeService.createOffer(dto.senderPlayerId(), dto.receiverPlayerId(),
                        dto.playerItemId(), dto.requestedZenny()));
    }

    @GetMapping("/received/{playerId}")
    public List<TradeOfferResponseDTO> listReceived(@PathVariable Long playerId) {
        return tradeService.listReceived(playerId).stream().map(TradeOfferResponseDTO::from).toList();
    }

    @GetMapping("/sent/{playerId}")
    public List<TradeOfferResponseDTO> listSent(@PathVariable Long playerId) {
        return tradeService.listSent(playerId).stream().map(TradeOfferResponseDTO::from).toList();
    }

    @PostMapping("/{id}/accept")
    public void accept(@PathVariable Long id, @RequestBody TradeActionRequestDTO dto) {
        tradeService.acceptOffer(id, dto.playerId());
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id, @RequestBody TradeActionRequestDTO dto) {
        tradeService.rejectOffer(id, dto.playerId());
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, @RequestBody TradeActionRequestDTO dto) {
        tradeService.cancelOffer(id, dto.playerId());
    }
}
```

- [ ] **Step 5: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=TradeControllerTest -q
```
Esperado: `Tests run: 3, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/TradeController.java \
        src/main/java/com/ragnarok/api/dto/request/CreateTradeOfferRequestDTO.java \
        src/main/java/com/ragnarok/api/dto/request/TradeActionRequestDTO.java \
        src/main/java/com/ragnarok/api/dto/response/TradeOfferResponseDTO.java \
        src/test/java/com/ragnarok/api/controller/TradeControllerTest.java
git commit -m "feat(trade): add TradeController and DTOs"
```

---

## Agente A3 — Loja NPC + Loja Cash

### A3-Task 1: CashShopItemEntity

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/CashShopItemEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/CashShopItemRepository.java`

- [ ] **Step 1: Criar entidade e repositório**

```java
// CashShopItemEntity.java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cash_shop_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CashShopItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "cash_price", nullable = false)
    private Long cashPrice;

    @Column(nullable = false)
    private boolean active = true;
}
```

```java
// CashShopItemRepository.java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashShopItemRepository extends JpaRepository<CashShopItemEntity, Long> {
    List<CashShopItemEntity> findByActiveTrue();
    long countByActiveTrue();
}
```

- [ ] **Step 2: Verificar compilação e commitar**

```bash
./mvnw compile -q
git add src/main/java/com/ragnarok/infrastructure/persistence/CashShopItemEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/CashShopItemRepository.java
git commit -m "feat(shop): add CashShopItemEntity and repository"
```

---

### A3-Task 2: NpcShopService

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/NpcShopService.java`
- Create: `src/test/java/com/ragnarok/application/service/NpcShopServiceTest.java`

- [ ] **Step 1: Escrever testes falhando**

```java
package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NpcShopServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;

    NpcShopService service;

    @BeforeEach
    void setUp() {
        service = new NpcShopService(itemRepository, playerRepository, playerItemRepository);
    }

    @Test
    void buy_success() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 500L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(playerItemRepository.findByPlayerIdAndItemId(1L, 1L)).thenReturn(List.of());

        service.buy(1L, 1L, 2);

        assertEquals(300L, player.getZenny()); // 500 - 100*2
        verify(playerItemRepository).save(any());
    }

    @Test
    void buy_insufficientZenny_throws() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 50L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        assertThrows(IllegalStateException.class, () -> service.buy(1L, 1L, 1));
    }

    @Test
    void sell_success() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 0L);
        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setId(UUID.randomUUID()); pi.setPlayer(player); pi.setItem(item); pi.setAmount(3);
        when(playerItemRepository.findById(pi.getId())).thenReturn(Optional.of(pi));

        service.sell(1L, pi.getId(), 2);

        assertEquals(100L, player.getZenny()); // 100 * 2 * 0.5
        assertEquals(1, pi.getAmount());
    }

    @Test
    void sell_moreThanOwned_throws() {
        ItemEntity item = item(1L, "Potion", 100);
        PlayerEntity player = player(1L, 0L);
        PlayerItemEntity pi = new PlayerItemEntity();
        pi.setId(UUID.randomUUID()); pi.setPlayer(player); pi.setItem(item); pi.setAmount(1);
        when(playerItemRepository.findById(pi.getId())).thenReturn(Optional.of(pi));

        assertThrows(IllegalStateException.class, () -> service.sell(1L, pi.getId(), 5));
    }

    private ItemEntity item(Long id, String name, int price) {
        ItemEntity e = new ItemEntity(); e.setId(id); e.setName(name); e.setPrice(price); return e;
    }
    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity(); p.setId(id); p.setZenny(zenny); return p;
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=NpcShopServiceTest -q
```

- [ ] **Step 3: Implementar NpcShopService**

```java
package com.ragnarok.application.service;

import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NpcShopService {

    private final ItemRepository itemRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;

    public NpcShopService(ItemRepository itemRepository,
                          PlayerRepository playerRepository,
                          PlayerItemRepository playerItemRepository) {
        this.itemRepository = itemRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
    }

    public List<ItemEntity> listItems() {
        return itemRepository.findAll();
    }

    @Transactional
    public void buy(Long playerId, Long itemId, int quantity) {
        ItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado: " + itemId));
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player nao encontrado"));

        long total = (long) item.getPrice() * quantity;
        if (player.getZenny() < total) {
            throw new IllegalStateException("Zenny insuficiente");
        }
        player.setZenny(player.getZenny() - total);
        playerRepository.save(player);

        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(playerId, itemId);
        if (!existing.isEmpty()) {
            PlayerItemEntity stack = existing.get(0);
            stack.setAmount(stack.getAmount() + quantity);
            playerItemRepository.save(stack);
        } else {
            PlayerItemEntity pi = new PlayerItemEntity();
            pi.setPlayer(player); pi.setItem(item);
            pi.setAmount(quantity); pi.setEquipped(false);
            playerItemRepository.save(pi);
        }
    }

    @Transactional
    public void sell(Long playerId, UUID playerItemId, int quantity) {
        PlayerItemEntity pi = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado no inventario"));
        if (!pi.getPlayer().getId().equals(playerId)) {
            throw new IllegalArgumentException("Item nao pertence a este jogador");
        }
        if (pi.getAmount() < quantity) {
            throw new IllegalStateException("Quantidade insuficiente no inventario");
        }
        long credit = (long) pi.getItem().getPrice() * quantity / 2;
        PlayerEntity player = pi.getPlayer();
        player.setZenny(player.getZenny() + credit);

        if (pi.getAmount() == quantity) {
            playerItemRepository.delete(pi);
        } else {
            pi.setAmount(pi.getAmount() - quantity);
            playerItemRepository.save(pi);
        }
        playerRepository.save(player);
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=NpcShopServiceTest -q
```
Esperado: `Tests run: 4, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/NpcShopService.java \
        src/test/java/com/ragnarok/application/service/NpcShopServiceTest.java
git commit -m "feat(shop): add NpcShopService with buy/sell logic"
```

---

### A3-Task 3: CashShopService

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/CashShopService.java`
- Create: `src/test/java/com/ragnarok/application/service/CashShopServiceTest.java`

> **Nota:** `AccountRepository` é criado pelo Agente A1. Durante desenvolvimento paralelo, declare a dependência e compile sem rodar testes de integração até A1 terminar.

- [ ] **Step 1: Escrever testes falhando**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashShopServiceTest {

    @Mock CashShopItemRepository cashShopItemRepository;
    @Mock AccountRepository accountRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock ItemRepository itemRepository;
    @Mock FraudClient fraudClient;

    CashShopService service;

    @BeforeEach
    void setUp() {
        service = new CashShopService(cashShopItemRepository, accountRepository,
                playerRepository, playerItemRepository, itemRepository, fraudClient);
    }

    @Test
    void buy_success() {
        CashShopItemEntity shopItem = cashShopItem(1L, 100L, 500L);
        AccountEntity account = account(10L, 1000L);
        PlayerEntity player = new PlayerEntity(); player.setId(1L);
        ItemEntity item = new ItemEntity(); item.setId(500L);

        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(itemRepository.findById(500L)).thenReturn(Optional.of(item));
        when(cashShopItemRepository.countByActiveTrue()).thenReturn(5L);
        when(fraudClient.checkMarketPurchase(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);
        when(playerItemRepository.findByPlayerIdAndItemId(anyLong(), anyLong())).thenReturn(List.of());

        service.buy(10L, 1L, 1L);

        assertEquals(900L, account.getCashPoints()); // 1000 - 100
    }

    @Test
    void buy_insufficientCashPoints_throws() {
        CashShopItemEntity shopItem = cashShopItem(1L, 500L, 100L);
        AccountEntity account = account(10L, 100L); // tem 100, precisa 500
        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class, () -> service.buy(10L, 1L, 1L));
    }

    @Test
    void buy_inactiveItem_throws() {
        CashShopItemEntity shopItem = cashShopItem(1L, 100L, 500L);
        shopItem.setActive(false);
        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));

        assertThrows(IllegalArgumentException.class, () -> service.buy(10L, 1L, 1L));
    }

    @Test
    void buy_blockedByFraud_throws() {
        CashShopItemEntity shopItem = cashShopItem(1L, 100L, 500L);
        AccountEntity account = account(10L, 1000L);
        when(cashShopItemRepository.findById(1L)).thenReturn(Optional.of(shopItem));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(cashShopItemRepository.countByActiveTrue()).thenReturn(5L);
        FraudClient.FraudDecision blocked = new FraudClient.FraudDecision(
                "x", 1L, "BLOCKED", "DROP_SESSION", "HIGH", List.of(), "fraud");
        when(fraudClient.checkMarketPurchase(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(blocked);

        assertThrows(GameException.class, () -> service.buy(10L, 1L, 1L));
    }

    private CashShopItemEntity cashShopItem(Long id, Long price, Long itemId) {
        CashShopItemEntity e = new CashShopItemEntity();
        e.setId(id); e.setCashPrice(price); e.setItemId(itemId); e.setActive(true);
        return e;
    }
    private AccountEntity account(Long id, Long cashPoints) {
        AccountEntity a = new AccountEntity();
        a.setId(id); a.setCashPoints(cashPoints);
        return a;
    }
}
```

- [ ] **Step 2: Implementar CashShopService**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CashShopService {

    private final CashShopItemRepository cashShopItemRepository;
    private final AccountRepository accountRepository; // REQUER: AccountRepository criado pelo Agente A1
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final ItemRepository itemRepository;
    private final FraudClient fraudClient;

    public CashShopService(CashShopItemRepository cashShopItemRepository,
                           AccountRepository accountRepository,
                           PlayerRepository playerRepository,
                           PlayerItemRepository playerItemRepository,
                           ItemRepository itemRepository,
                           FraudClient fraudClient) {
        this.cashShopItemRepository = cashShopItemRepository;
        this.accountRepository = accountRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemRepository = itemRepository;
        this.fraudClient = fraudClient;
    }

    public List<CashShopItemEntity> listItems() {
        return cashShopItemRepository.findByActiveTrue();
    }

    @Transactional
    public void buy(Long accountId, Long cashShopItemId, Long playerId) {
        CashShopItemEntity shopItem = cashShopItemRepository.findById(cashShopItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado na loja cash"));
        if (!shopItem.isActive()) {
            throw new IllegalArgumentException("Item nao esta disponivel na loja");
        }
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Conta nao encontrada"));
        if (account.getCashPoints() < shopItem.getCashPrice()) {
            throw new IllegalStateException("Cash Points insuficientes");
        }

        long totalActive = cashShopItemRepository.countByActiveTrue();
        FraudClient.FraudDecision decision = fraudClient.checkMarketPurchase(
                playerId, shopItem.getItemId(), 1L, totalActive);
        if (decision.isBlocked()) {
            throw new GameException("Compra bloqueada pelo sistema antifraude");
        }

        account.setCashPoints(account.getCashPoints() - shopItem.getCashPrice());
        accountRepository.save(account);

        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        ItemEntity item = itemRepository.findById(shopItem.getItemId()).orElseThrow();

        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(playerId, item.getId());
        if (!existing.isEmpty()) {
            existing.get(0).setAmount(existing.get(0).getAmount() + 1);
            playerItemRepository.save(existing.get(0));
        } else {
            PlayerItemEntity pi = new PlayerItemEntity();
            pi.setPlayer(player); pi.setItem(item); pi.setAmount(1); pi.setEquipped(false);
            playerItemRepository.save(pi);
        }
    }
}
```

- [ ] **Step 3: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=CashShopServiceTest -q
```
Esperado: `Tests run: 4, Failures: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/CashShopService.java \
        src/test/java/com/ragnarok/application/service/CashShopServiceTest.java
git commit -m "feat(shop): add CashShopService with cashPoints validation and fraud check"
```

---

### A3-Task 4: NpcShopController + CashShopController + DTOs

**Files:**
- Create: `src/main/java/com/ragnarok/api/dto/request/NpcBuyRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/request/NpcSellRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/request/CashBuyRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/response/ShopItemResponseDTO.java`
- Create: `src/main/java/com/ragnarok/api/controller/NpcShopController.java`
- Create: `src/main/java/com/ragnarok/api/controller/CashShopController.java`
- Create: `src/test/java/com/ragnarok/api/controller/NpcShopControllerTest.java`
- Create: `src/test/java/com/ragnarok/api/controller/CashShopControllerTest.java`

- [ ] **Step 1: Criar DTOs**

```java
// NpcBuyRequestDTO.java
package com.ragnarok.api.dto.request;
public record NpcBuyRequestDTO(Long playerId, Long itemId, Integer quantity) {}

// NpcSellRequestDTO.java
package com.ragnarok.api.dto.request;
import java.util.UUID;
public record NpcSellRequestDTO(Long playerId, UUID playerItemId, Integer quantity) {}

// CashBuyRequestDTO.java
package com.ragnarok.api.dto.request;
public record CashBuyRequestDTO(Long accountId, Long playerId, Long cashShopItemId) {}

// ShopItemResponseDTO.java
package com.ragnarok.api.dto.response;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.CashShopItemEntity;
public record ShopItemResponseDTO(Long id, String name, Integer price, Long cashPrice) {
    public static ShopItemResponseDTO fromItem(ItemEntity e) {
        return new ShopItemResponseDTO(e.getId(), e.getName(), e.getPrice(), null);
    }
    public static ShopItemResponseDTO fromCashShopItem(CashShopItemEntity e, String itemName) {
        return new ShopItemResponseDTO(e.getId(), itemName, null, e.getCashPrice());
    }
}
```

- [ ] **Step 2: Criar controllers**

```java
// NpcShopController.java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.ShopItemResponseDTO;
import com.ragnarok.application.service.NpcShopService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/shop/npc")
public class NpcShopController {

    private final NpcShopService npcShopService;

    public NpcShopController(NpcShopService npcShopService) {
        this.npcShopService = npcShopService;
    }

    @GetMapping("/items")
    public List<ShopItemResponseDTO> list() {
        return npcShopService.listItems().stream().map(ShopItemResponseDTO::fromItem).toList();
    }

    @PostMapping("/buy")
    public void buy(@RequestBody NpcBuyRequestDTO dto) {
        npcShopService.buy(dto.playerId(), dto.itemId(), dto.quantity());
    }

    @PostMapping("/sell")
    public void sell(@RequestBody NpcSellRequestDTO dto) {
        npcShopService.sell(dto.playerId(), dto.playerItemId(), dto.quantity());
    }
}
```

```java
// CashShopController.java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.CashBuyRequestDTO;
import com.ragnarok.api.dto.response.ShopItemResponseDTO;
import com.ragnarok.application.service.CashShopService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/shop/cash")
public class CashShopController {

    private final CashShopService cashShopService;

    public CashShopController(CashShopService cashShopService) {
        this.cashShopService = cashShopService;
    }

    @GetMapping("/items")
    public List<ShopItemResponseDTO> list() {
        return cashShopService.listItems().stream()
                .map(e -> ShopItemResponseDTO.fromCashShopItem(e, "Item #" + e.getItemId()))
                .toList();
    }

    @PostMapping("/buy")
    public void buy(@RequestBody CashBuyRequestDTO dto) {
        cashShopService.buy(dto.accountId(), dto.cashShopItemId(), dto.playerId());
    }
}
```

- [ ] **Step 3: Escrever e rodar testes de controller**

```java
// NpcShopControllerTest.java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.NpcShopService;
import com.ragnarok.api.dto.request.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NpcShopController.class)
class NpcShopControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NpcShopService npcShopService;

    @Test
    void listItems_returns200() throws Exception {
        when(npcShopService.listItems()).thenReturn(List.of());
        mockMvc.perform(get("/api/shop/npc/items")).andExpect(status().isOk());
    }

    @Test
    void buy_returns200() throws Exception {
        doNothing().when(npcShopService).buy(any(), any(), any());
        mockMvc.perform(post("/api/shop/npc/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NpcBuyRequestDTO(1L, 1L, 1))))
                .andExpect(status().isOk());
    }
}
```

```bash
./mvnw test -Djacoco.skip=true -Dtest="NpcShopControllerTest,CashShopControllerTest" -q
```
Esperado: todos passando

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/NpcShopController.java \
        src/main/java/com/ragnarok/api/controller/CashShopController.java \
        src/main/java/com/ragnarok/api/dto/ \
        src/test/java/com/ragnarok/api/controller/NpcShopControllerTest.java \
        src/test/java/com/ragnarok/api/controller/CashShopControllerTest.java
git commit -m "feat(shop): add NpcShopController, CashShopController and DTOs"
```

---

## ONDA 2 — Agentes B1, B2 em paralelo (após Onda 1 completa)

> Antes de iniciar: verificar que todos os commits de A1, A2, A3 estão na branch.

---

## Agente B1 — Mercado entre jogadores

### B1-Task 1: MarketListingEntity + MarketListingRepository

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/MarketListingEntity.java`
- Create: `src/main/java/com/ragnarok/infrastructure/persistence/MarketListingRepository.java`

- [ ] **Step 1: Criar entidade**

```java
package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_listings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MarketListingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_player_id", nullable = false)
    private Long sellerPlayerId;

    @Column(name = "player_item_id", nullable = false)
    private UUID playerItemId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "price_zenny", nullable = false)
    private Long priceZenny;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / SOLD / CANCELLED

    @Column(name = "listed_at")
    private Instant listedAt = Instant.now();

    @Column(name = "sold_at")
    private Instant soldAt;
}
```

- [ ] **Step 2: Criar repositório**

```java
package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarketListingRepository extends JpaRepository<MarketListingEntity, Long> {
    List<MarketListingEntity> findByStatus(String status);
    List<MarketListingEntity> findByItemIdAndStatus(Long itemId, String status);
    long countByStatus(String status);
}
```

- [ ] **Step 3: Compilar e commitar**

```bash
./mvnw compile -q
git add src/main/java/com/ragnarok/infrastructure/persistence/MarketListingEntity.java \
        src/main/java/com/ragnarok/infrastructure/persistence/MarketListingRepository.java
git commit -m "feat(market): add MarketListingEntity and repository"
```

---

### B1-Task 2: MarketService

**Files:**
- Create: `src/main/java/com/ragnarok/application/service/MarketService.java`
- Create: `src/test/java/com/ragnarok/application/service/MarketServiceTest.java`

- [ ] **Step 1: Escrever testes falhando**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock MarketListingRepository marketListingRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerItemRepository playerItemRepository;
    @Mock FraudClient fraudClient;

    MarketService service;

    @BeforeEach
    void setUp() {
        service = new MarketService(marketListingRepository, playerRepository, playerItemRepository, fraudClient);
    }

    @Test
    void createListing_success() {
        UUID piId = UUID.randomUUID();
        PlayerItemEntity pi = playerItem(piId, player(1L, 0L), 100L, 5);
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));
        when(fraudClient.checkItemTrade(anyLong(), anyLong(), anyString(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);
        when(marketListingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.createListing(1L, piId, 200L, 3));
        verify(marketListingRepository).save(any());
    }

    @Test
    void createListing_insufficientQuantity_throws() {
        UUID piId = UUID.randomUUID();
        PlayerItemEntity pi = playerItem(piId, player(1L, 0L), 100L, 2); // tem 2, pede 5
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));

        assertThrows(IllegalStateException.class, () -> service.createListing(1L, piId, 100L, 5));
    }

    @Test
    void buy_success() {
        UUID piId = UUID.randomUUID();
        PlayerEntity seller = player(1L, 0L);
        PlayerEntity buyer = player(2L, 500L);
        PlayerItemEntity pi = playerItem(piId, seller, 100L, 3);
        MarketListingEntity listing = listing(1L, 1L, piId, 100L, 200L, 2);

        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(buyer));
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));
        when(marketListingRepository.countByStatus("ACTIVE")).thenReturn(10L);
        when(fraudClient.checkMarketPurchase(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(FraudClient.FraudDecision.FALLBACK_APPROVED);
        when(playerItemRepository.findByPlayerIdAndItemId(anyLong(), anyLong())).thenReturn(List.of());

        service.buy(1L, 2L);

        assertEquals(300L, buyer.getZenny()); // 500 - 200
        assertEquals(200L, seller.getZenny()); // 0 + 200
        assertEquals("SOLD", listing.getStatus());
    }

    @Test
    void buy_selfPurchase_throws() {
        MarketListingEntity listing = listing(1L, 1L, UUID.randomUUID(), 100L, 200L, 1);
        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThrows(IllegalArgumentException.class, () -> service.buy(1L, 1L)); // buyer == seller
    }

    @Test
    void buy_insufficientZenny_throws() {
        UUID piId = UUID.randomUUID();
        PlayerEntity seller = player(1L, 0L);
        PlayerEntity buyer = player(2L, 50L); // tem 50, precisa 200
        PlayerItemEntity pi = playerItem(piId, seller, 100L, 2);
        MarketListingEntity listing = listing(1L, 1L, piId, 100L, 200L, 2);

        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(buyer));
        when(playerItemRepository.findById(piId)).thenReturn(Optional.of(pi));

        assertThrows(IllegalStateException.class, () -> service.buy(1L, 2L));
    }

    @Test
    void cancel_success() {
        MarketListingEntity listing = listing(1L, 1L, UUID.randomUUID(), 100L, 200L, 1);
        when(marketListingRepository.findById(1L)).thenReturn(Optional.of(listing));

        service.cancel(1L, 1L);

        assertEquals("CANCELLED", listing.getStatus());
    }

    // helpers
    private PlayerEntity player(Long id, Long zenny) {
        PlayerEntity p = new PlayerEntity(); p.setId(id); p.setZenny(zenny); return p;
    }
    private PlayerItemEntity playerItem(UUID id, PlayerEntity owner, Long itemId, int amount) {
        PlayerItemEntity pi = new PlayerItemEntity(); pi.setId(id); pi.setPlayer(owner);
        ItemEntity item = new ItemEntity(); item.setId(itemId); pi.setItem(item); pi.setAmount(amount);
        return pi;
    }
    private MarketListingEntity listing(Long id, Long sellerId, UUID piId, Long itemId, Long price, int qty) {
        MarketListingEntity l = new MarketListingEntity();
        l.setId(id); l.setSellerPlayerId(sellerId); l.setPlayerItemId(piId);
        l.setItemId(itemId); l.setPriceZenny(price); l.setQuantity(qty); l.setStatus("ACTIVE");
        return l;
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
./mvnw test -Djacoco.skip=true -Dtest=MarketServiceTest -q
```

- [ ] **Step 3: Implementar MarketService**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MarketService {

    private final MarketListingRepository marketListingRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final FraudClient fraudClient;

    public MarketService(MarketListingRepository marketListingRepository,
                         PlayerRepository playerRepository,
                         PlayerItemRepository playerItemRepository,
                         FraudClient fraudClient) {
        this.marketListingRepository = marketListingRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.fraudClient = fraudClient;
    }

    public List<MarketListingEntity> listActive(Long itemId) {
        if (itemId != null) {
            return marketListingRepository.findByItemIdAndStatus(itemId, "ACTIVE");
        }
        return marketListingRepository.findByStatus("ACTIVE");
    }

    @Transactional
    public MarketListingEntity createListing(Long sellerPlayerId, UUID playerItemId,
                                              Long priceZenny, int quantity) {
        PlayerItemEntity pi = playerItemRepository.findById(playerItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado no inventario"));
        if (pi.getAmount() < quantity) {
            throw new IllegalStateException("Quantidade insuficiente no inventario");
        }

        FraudClient.FraudDecision decision = fraudClient.checkItemTrade(
                sellerPlayerId, pi.getItem().getId(), playerItemId.toString(), priceZenny);
        if (decision.isBlocked()) {
            throw new GameException("Listagem bloqueada pelo sistema antifraude");
        }

        MarketListingEntity listing = new MarketListingEntity();
        listing.setSellerPlayerId(sellerPlayerId);
        listing.setPlayerItemId(playerItemId);
        listing.setItemId(pi.getItem().getId());
        listing.setPriceZenny(priceZenny);
        listing.setQuantity(quantity);
        return marketListingRepository.save(listing);
    }

    @Transactional
    public void buy(Long listingId, Long buyerPlayerId) {
        MarketListingEntity listing = marketListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listagem nao encontrada"));
        if (!"ACTIVE".equals(listing.getStatus())) {
            throw new IllegalStateException("Listagem nao esta mais ativa");
        }
        if (listing.getSellerPlayerId().equals(buyerPlayerId)) {
            throw new IllegalArgumentException("Voce nao pode comprar seu proprio item");
        }

        PlayerEntity seller = playerRepository.findById(listing.getSellerPlayerId()).orElseThrow();
        PlayerEntity buyer = playerRepository.findById(buyerPlayerId).orElseThrow();

        if (buyer.getZenny() < listing.getPriceZenny()) {
            throw new IllegalStateException("Zenny insuficiente");
        }

        PlayerItemEntity sellerItem = playerItemRepository.findById(listing.getPlayerItemId())
                .orElseThrow(() -> new IllegalStateException("Item nao esta mais no inventario do vendedor"));

        long totalActive = marketListingRepository.countByStatus("ACTIVE");
        FraudClient.FraudDecision decision = fraudClient.checkMarketPurchase(
                buyerPlayerId, listing.getItemId(), (long) listing.getQuantity(), totalActive);
        if (decision.isBlocked()) {
            throw new GameException("Compra bloqueada pelo sistema antifraude");
        }

        // transfere item
        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(
                buyerPlayerId, listing.getItemId());
        if (!existing.isEmpty()) {
            existing.get(0).setAmount(existing.get(0).getAmount() + listing.getQuantity());
            playerItemRepository.save(existing.get(0));
        } else {
            PlayerItemEntity newPi = new PlayerItemEntity();
            newPi.setPlayer(buyer); newPi.setItem(sellerItem.getItem());
            newPi.setAmount(listing.getQuantity()); newPi.setEquipped(false);
            playerItemRepository.save(newPi);
        }

        if (sellerItem.getAmount() <= listing.getQuantity()) {
            playerItemRepository.delete(sellerItem);
        } else {
            sellerItem.setAmount(sellerItem.getAmount() - listing.getQuantity());
            playerItemRepository.save(sellerItem);
        }

        buyer.setZenny(buyer.getZenny() - listing.getPriceZenny());
        seller.setZenny(seller.getZenny() + listing.getPriceZenny());
        playerRepository.save(buyer);
        playerRepository.save(seller);

        listing.setStatus("SOLD");
        listing.setSoldAt(Instant.now());
        marketListingRepository.save(listing);
    }

    @Transactional
    public void cancel(Long listingId, Long sellerPlayerId) {
        MarketListingEntity listing = marketListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listagem nao encontrada"));
        if (!listing.getSellerPlayerId().equals(sellerPlayerId)) {
            throw new IllegalArgumentException("Apenas o vendedor pode cancelar a listagem");
        }
        listing.setStatus("CANCELLED");
        marketListingRepository.save(listing);
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
./mvnw test -Djacoco.skip=true -Dtest=MarketServiceTest -q
```
Esperado: `Tests run: 6, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/application/service/MarketService.java \
        src/test/java/com/ragnarok/application/service/MarketServiceTest.java
git commit -m "feat(market): add MarketService with listing/buy/cancel and fraud checks"
```

---

### B1-Task 3: MarketController + DTOs

**Files:**
- Create: `src/main/java/com/ragnarok/api/dto/request/CreateListingRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/request/BuyListingRequestDTO.java`
- Create: `src/main/java/com/ragnarok/api/dto/response/MarketListingResponseDTO.java`
- Create: `src/main/java/com/ragnarok/api/controller/MarketController.java`
- Create: `src/test/java/com/ragnarok/api/controller/MarketControllerTest.java`

- [ ] **Step 1: Criar DTOs**

```java
// CreateListingRequestDTO.java
package com.ragnarok.api.dto.request;
import java.util.UUID;
public record CreateListingRequestDTO(Long sellerPlayerId, UUID playerItemId,
                                       Long priceZenny, Integer quantity) {}

// BuyListingRequestDTO.java
package com.ragnarok.api.dto.request;
public record BuyListingRequestDTO(Long buyerPlayerId) {}

// MarketListingResponseDTO.java
package com.ragnarok.api.dto.response;
import com.ragnarok.infrastructure.persistence.MarketListingEntity;
import java.util.UUID;
public record MarketListingResponseDTO(Long id, Long sellerPlayerId, UUID playerItemId,
                                        Long itemId, Long priceZenny, Integer quantity, String status) {
    public static MarketListingResponseDTO from(MarketListingEntity e) {
        return new MarketListingResponseDTO(e.getId(), e.getSellerPlayerId(), e.getPlayerItemId(),
                e.getItemId(), e.getPriceZenny(), e.getQuantity(), e.getStatus());
    }
}
```

- [ ] **Step 2: Criar MarketController**

```java
package com.ragnarok.api.controller;

import com.ragnarok.api.dto.request.*;
import com.ragnarok.api.dto.response.MarketListingResponseDTO;
import com.ragnarok.application.service.MarketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market/listings")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    public List<MarketListingResponseDTO> list(@RequestParam(required = false) Long itemId) {
        return marketService.listActive(itemId).stream().map(MarketListingResponseDTO::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarketListingResponseDTO create(@RequestBody CreateListingRequestDTO dto) {
        return MarketListingResponseDTO.from(
                marketService.createListing(dto.sellerPlayerId(), dto.playerItemId(),
                        dto.priceZenny(), dto.quantity()));
    }

    @PostMapping("/{id}/buy")
    public void buy(@PathVariable Long id, @RequestBody BuyListingRequestDTO dto) {
        marketService.buy(id, dto.buyerPlayerId());
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, @RequestBody BuyListingRequestDTO dto) {
        marketService.cancel(id, dto.buyerPlayerId());
    }
}
```

- [ ] **Step 3: Escrever e rodar testes**

```java
// MarketControllerTest.java
package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.application.service.MarketService;
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

    @Test
    void list_returns200() throws Exception {
        when(marketService.listActive(null)).thenReturn(List.of());
        mockMvc.perform(get("/api/market/listings")).andExpect(status().isOk());
    }

    @Test
    void listWithFilter_returns200() throws Exception {
        when(marketService.listActive(500L)).thenReturn(List.of());
        mockMvc.perform(get("/api/market/listings?itemId=500")).andExpect(status().isOk());
    }
}
```

```bash
./mvnw test -Djacoco.skip=true -Dtest=MarketControllerTest -q
```
Esperado: `Tests run: 2, Failures: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/MarketController.java \
        src/main/java/com/ragnarok/api/dto/request/CreateListingRequestDTO.java \
        src/main/java/com/ragnarok/api/dto/request/BuyListingRequestDTO.java \
        src/main/java/com/ragnarok/api/dto/response/MarketListingResponseDTO.java \
        src/test/java/com/ragnarok/api/controller/MarketControllerTest.java
git commit -m "feat(market): add MarketController and DTOs"
```

---

## Agente B2 — Wiring do JWT

### B2-Task 1: FilterConfig

**Files:**
- Create: `src/main/java/com/ragnarok/infrastructure/config/FilterConfig.java`

- [ ] **Step 1: Criar FilterConfig**

```java
package com.ragnarok.infrastructure.config;

import com.ragnarok.infrastructure.security.JwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter jwtFilter) {
        FilterRegistrationBean<JwtFilter> reg = new FilterRegistrationBean<>(jwtFilter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
```

- [ ] **Step 2: Verificar compilação**

```bash
./mvnw compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ragnarok/infrastructure/config/FilterConfig.java
git commit -m "feat(auth-wiring): register JwtFilter for /api/* routes"
```

---

### B2-Task 2: Adicionar validateOwnership nos controllers existentes (Feature 4)

> Modificar os 5 controllers existentes: PlayerController, BattleController, SkillController, ItemController, MapController.
> Padrão: injetar `AccountService` + `HttpServletRequest`, chamar `validateOwnership` antes de cada ação.

**Files:**
- Modify: cada controller existente em `src/main/java/com/ragnarok/api/controller/`

- [ ] **Step 1: Identificar os controllers existentes**

```bash
find src/main/java/com/ragnarok/api/controller -name "*.java" | sort
```

- [ ] **Step 2: Para cada controller, adicionar injeção de AccountService e HttpServletRequest**

Padrão a aplicar em cada controller que recebe `playerId` como `@PathVariable` ou no body:

```java
// Adicionar ao construtor:
private final AccountService accountService;

// Adicionar a cada método que recebe playerId:
Long accountId = (Long) request.getAttribute("accountId");
if (accountId != null) { // null durante testes sem filtro ativo
    accountService.validateOwnership(accountId, playerId);
}
```

- [ ] **Step 3: Atualizar testes existentes dos controllers**

Nos `@WebMvcTest` existentes, adicionar `@MockBean AccountService accountService` e, onde necessário, `request.setAttribute("accountId", 1L)` via `MockMvcRequestBuilders.get(...).requestAttr("accountId", 1L)`.

- [ ] **Step 4: Rodar todos os testes de controllers**

```bash
./mvnw test -Djacoco.skip=true \
  -Dtest="PlayerControllerTest,BattleControllerTest,SkillControllerTest,ItemControllerTest,MapControllerTest" -q
```
Esperado: todos passando

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ragnarok/api/controller/
git commit -m "feat(auth-wiring): add validateOwnership to existing controllers"
```

---

### B2-Task 3: Verificação final

- [ ] **Step 1: Rodar suite completa de testes**

```bash
./mvnw test -Djacoco.skip=true \
  -Dtest="JwtUtilTest,JwtFilterTest,AccountServiceTest,AccountControllerTest,\
TradeServiceTest,TradeControllerTest,\
NpcShopServiceTest,CashShopServiceTest,NpcShopControllerTest,CashShopControllerTest,\
MarketServiceTest,MarketControllerTest,\
PlayerControllerTest,BattleControllerTest,SkillControllerTest,ItemControllerTest,MapControllerTest" -q
```
Esperado: BUILD SUCCESS, 0 falhas

- [ ] **Step 2: Rodar com JaCoCo para verificar coverage**

```bash
./mvnw test -q
```
Esperado: BUILD SUCCESS (≥85% line, ≥62% branch)

- [ ] **Step 3: Commit final**

```bash
git add .
git commit -m "feat(auth-wiring): complete JWT wiring — all controllers protected"
```

---

## Resumo de execução

| Onda | Agentes | Em paralelo? | Pré-requisito |
|------|---------|--------------|---------------|
| 1 | A1 (JWT), A2 (Trade), A3 (Shop) | Sim | nenhum |
| 2 | B1 (Market), B2 (Wiring) | Sim | Onda 1 completa |

**Ordem sugerida para dispatching:**
1. Despachar A1, A2, A3 simultaneamente via `superpowers:dispatching-parallel-agents`
2. Aguardar todos completarem
3. Merge dos branches de cada agente
4. Despachar B1, B2 simultaneamente
5. Merge final + verificação de coverage
