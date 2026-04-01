# Design Spec — ragnarok-core Expansion
**Date:** 2026-03-25
**Branch:** feature/Alt-05 (base para implementação)
**Status:** Aprovado pelo usuário

---

## Contexto

O ragnarok-core é um backend de RPG estilo Ragnarok Online em Spring Boot 3.4.2 / Java 17. A branch atual tem 5 features de portfolio implementadas (Resilience4j, Testcontainers, Spring Cache, REST API, Spring Events) e um `FraudClient` integrado em `BattleService`, `MapService` e `PlayerController`.

Este documento especifica a implementação de 4 novos sistemas em duas ondas paralelas.

---

## Restrições globais

- Java 17, Spring Boot 3.4.2
- Sem Spring Security — auth via filtro customizado `OncePerRequestFilter`
- `FraudClient` já existe em `com.ragnarok.infrastructure.antifraude.FraudClient` — não recriar
- `ddl-auto=update` — Hibernate cria tabelas novas automaticamente via `@Entity`
- JaCoCo: >= 85% line coverage, >= 62% branch coverage — testes obrigatórios em cada sistema
- Padrão do projeto: `@Entity` -> `@Repository` -> `@Service` -> `@RestController`
- Sem complexidade desnecessária — cada sistema simples e funcional

---

## Modelo de ondas e agentes

### Onda 1 — paralelo (sem dependências entre si)

| Agente | Sistema |
|--------|---------|
| A1 | Login / JWT |
| A2 | Troca entre jogadores |
| A3 | Loja NPC + Loja Cash |

### Onda 2 — paralelo (após Onda 1 completa)

| Agente | Sistema |
|--------|---------|
| B1 | Mercado entre jogadores |
| B2 | Wiring do JWT nos endpoints |

---

## Sistema 1 — Login / JWT (Agente A1)

### Decisões de design

- JWT carrega apenas `accountId` (não `playerId`) — um account pode ter N personagens
- `playerId` continua sendo passado nos endpoints como path param
- Ownership validation: `accountService.validateOwnership(accountId, playerId)` feita no controller
- JWT signed com HS256, chave em `app.jwt.secret` no `application.properties`
- TTL: 24h

### Nova entidade: AccountEntity

Tabela: `accounts`

| Campo | Tipo Java | Coluna DB | Observação |
|-------|-----------|-----------|------------|
| `id` | Long | `id` PK auto | |
| `username` | String | `username` unique not null | |
| `passwordHash` | String | `password_hash` | BCrypt |
| `email` | String | `email` | nullable |
| `emailVerified` | boolean | `email_verified` | default false |
| `ageVerified` | boolean | `age_verified` | default false |
| `cashPoints` | Long | `cash_points` | default 0 |
| `createdAt` | Instant | `created_at` | |

### Alteração em PlayerEntity

Novo campo: `accountId` (Long, nullable, FK para `accounts.id`). Nullable para preservar players existentes sem conta.

### Novos arquivos

```
infrastructure/persistence/
  AccountEntity.java
  AccountRepository.java

application/service/
  AccountService.java       — register(), login(), validateOwnership()

infrastructure/security/
  JwtUtil.java              — generateToken(accountId), extractAccountId(token), isValid(token)
  JwtFilter.java            — OncePerRequestFilter; bypass em /api/accounts/**

api/
  GlobalExceptionHandler.java  — @RestControllerAdvice; adiciona handler: IllegalStateException -> 409
                                  (O GlobalExceptionHandler ja existe no projeto da Feature 4;
                                   A1 apenas adiciona o handler de IllegalStateException -> 409)
api/controller/
  AccountController.java    — POST /api/accounts/register, POST /api/accounts/login
api/dto/request/
  RegisterRequestDTO.java
  LoginRequestDTO.java
api/dto/response/
  LoginResponseDTO.java     — { token, accountId }
```

### Dependências novas no pom.xml

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
<!-- BCrypt (sem Spring Security completo) -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

> `spring-security-crypto` fornece `BCryptPasswordEncoder` sem puxar o módulo de autenticacao/filtros do Spring Security. A versao e gerenciada pelo Spring Boot BOM.

### Endpoints

```
POST /api/accounts/register   — { username, password, email? }  -> 201 { accountId }
POST /api/accounts/login      — { username, password }          -> 200 { token, accountId }
```

### Fluxo de registro

```
AccountService.register(username, password, email)
  1. valida username unico — se duplicado: lanca IllegalStateException("Username ja existe")
     GlobalExceptionHandler mapeia IllegalStateException -> 409 Conflict
  2. BCrypt na senha (BCryptPasswordEncoder de spring-security-crypto)
  3. salva AccountEntity
  4. fraudClient.syncRegistrationAsync(accountId, false, false, null)
```

> **Nota:** username duplicado retorna **409 Conflict**, nao 404. Requer adicionar handler em `GlobalExceptionHandler` para `IllegalStateException -> 409`.

### Fluxo de login

```
AccountService.login(username, password, ipAddress)
  1. busca account por username (lanca se nao encontrado)
  2. valida senha com BCrypt
  3. fraudClient.checkLogin(accountId, ipAddress, "BR", emailVerified, ageVerified)
     -> se BLOCKED: lanca GameException ("Acesso bloqueado pelo sistema antifraude")
  4. retorna JwtUtil.generateToken(accountId)
```

### JwtFilter

```
JwtFilter.doFilterInternal()
  - rotas publicas (bypass): POST /api/accounts/register, POST /api/accounts/login
  - extrai header "Authorization: Bearer <token>"
  - JwtUtil.isValid(token) -> se invalido: responde 401
  - extrai accountId e coloca em request.setAttribute("accountId", accountId)
  - chain.doFilter()
```

### validateOwnership

```
AccountService.validateOwnership(accountId, playerId)
  - busca PlayerEntity por playerId
  - se player.accountId != accountId: lanca IllegalArgumentException -> 404
```

### Testes obrigatorios

- `AccountControllerTest` — register duplicado, login correto, login senha errada, login bloqueado pelo fraud
- `AccountServiceTest` — validateOwnership correto e incorreto
- `JwtFilterTest` — token ausente, token invalido, rota publica bypass

---

## Sistema 2 — Troca entre jogadores (Agente A2)

### Decisoes de design

- Troca assíncrona por oferta: sender cria oferta, receiver aceita/rejeita
- FraudClient chamado apenas no `accept` (quando ativos mudam de mão)
- Toda a troca ocorre em uma unica `@Transactional`
- Zenny pode ser 0 (presente sem contrapartida)

### Nova entidade: TradeOfferEntity

Tabela: `trade_offers`

| Campo | Tipo Java | Coluna DB | Observação |
|-------|-----------|-----------|------------|
| `id` | Long | `id` PK auto | |
| `senderPlayerId` | Long | `sender_player_id` FK players | |
| `receiverPlayerId` | Long | `receiver_player_id` FK players | |
| `offeredPlayerItemId` | UUID | `offered_player_item_id` FK player_items | |
| `requestedZenny` | Long | `requested_zenny` | >= 0 |
| `status` | String | `status` | PENDING / ACCEPTED / REJECTED / CANCELLED |
| `createdAt` | Instant | `created_at` | |

### Novos arquivos

```
infrastructure/persistence/
  3TradeOfferEntity.java
  TradeOfferRepository.java

application/service/
  TradeService.java

api/controller/
  TradeController.java
api/dto/request/
  CreateTradeOfferRequestDTO.java   — { senderPlayerId, receiverPlayerId, playerItemId, requestedZenny }
  TradeActionRequestDTO.java        — { playerId }
api/dto/response/
  TradeOfferResponseDTO.java
```

### Endpoints

```
POST /api/trade/offers                       — criar oferta
GET  /api/trade/offers/received/{playerId}   — ofertas recebidas (status=PENDING)
GET  /api/trade/offers/sent/{playerId}       — ofertas enviadas
POST /api/trade/offers/{id}/accept           — body: { playerId }
POST /api/trade/offers/{id}/reject           — body: { playerId }
POST /api/trade/offers/{id}/cancel           — body: { playerId } (so o sender)
```

### Fluxo de aceite (atomic @Transactional)

```
TradeService.acceptOffer(offerId, receiverPlayerId)
  1. carrega offer; valida status == PENDING
  2. valida receiverPlayerId == offer.receiverPlayerId
  3. valida sender ainda possui o item (playerItemRepo.findById)
  4. valida receiver tem zenny >= offer.requestedZenny
  5. fraudClient.checkItemTrade(senderPlayerId, itemId, itemUuid.toString(), requestedZenny)
     -> se BLOCKED: lanca GameException
  6. playerItem.setPlayer(receiverEntity)
  7. receiver.zenny -= requestedZenny; sender.zenny += requestedZenny
  8. offer.status = ACCEPTED
  9. salva offer, playerItem, sender, receiver
```

### Testes obrigatorios

- `TradeServiceTest` — criar oferta, aceitar com sucesso, aceitar sem item (sender perdeu), aceitar sem zenny, rejeitar, cancelar por nao-sender
- `TradeControllerTest` — todos os endpoints, status codes

---

## Sistema 3 — Loja NPC + Loja Cash (Agente A3)

### Decisoes de design

- Loja NPC: usa `ItemEntity` existente como catalogo; preco de compra = `item.price`; preco de venda = `item.price * 0.5`
- Loja Cash: nova entidade `CashShopItemEntity`; moeda = `AccountEntity.cashPoints`
- Agente A3 cria `AccountEntity` como stub com apenas `id` + `cashPoints`; Agente A1 completa a entidade

### Nova entidade: CashShopItemEntity

Tabela: `cash_shop_items`

| Campo | Tipo Java | Coluna DB | Observação |
|-------|-----------|-----------|------------|
| `id` | Long | `id` PK auto | |
| `itemId` | Long | `item_id` FK items | |
| `cashPrice` | Long | `cash_price` | |
| `active` | boolean | `active` | default true |

### Novos arquivos

```
application/service/
  NpcShopService.java
  CashShopService.java

api/controller/
  NpcShopController.java
  CashShopController.java
api/dto/request/
  NpcBuyRequestDTO.java    — { playerId, itemId, quantity }
  NpcSellRequestDTO.java   — { playerId, playerItemId, quantity }
  CashBuyRequestDTO.java   — { accountId, playerId, itemId }
api/dto/response/
  ShopItemResponseDTO.java
```

### Endpoints — Loja NPC

```
GET  /api/shop/npc/items   — lista todos os itens (id, name, price, type)
POST /api/shop/npc/buy     — compra com zenny
POST /api/shop/npc/sell    — vende ao NPC por 50% do preco
```

### Fluxo de compra NPC

```
NpcShopService.buy(playerId, itemId, quantity)
  1. busca item; valida existencia
  2. valida player.zenny >= item.price * quantity
  3. debita zenny
  4. cria/incrementa PlayerItemEntity (mesmo padrao do BattleService)
  5. salva player + playerItem
  // sem FraudClient — preco fixo controlado
```

### Fluxo de venda NPC

```
NpcShopService.sell(playerId, playerItemId, quantity)
  1. busca playerItem; valida ownership (player.id == playerId)
  2. valida playerItem.amount >= quantity
  3. credita player.zenny += item.price * quantity * 0.5 (int division)
  4. decrementa ou deleta playerItem
  5. salva
```

### Endpoints — Loja Cash

```
GET  /api/shop/cash/items  — lista itens com active=true
POST /api/shop/cash/buy    — body: { accountId, playerId, itemId }
```

### Fluxo de compra Cash

```
CashShopService.buy(accountId, playerId, itemId)
  1. busca CashShopItem; valida active=true
  2. busca Account; valida account.cashPoints >= cashShopItem.cashPrice
  3. fraudClient.checkMarketPurchase(playerId, itemId, 1L, totalActive)
     -> se BLOCKED: lanca GameException
  4. account.cashPoints -= cashShopItem.cashPrice
  5. cria PlayerItemEntity para o player
  6. salva account + playerItem
```

### Testes obrigatorios

- `NpcShopServiceTest` — compra com sucesso, zenny insuficiente, venda com sucesso, venda quantidade maior que possui
- `CashShopServiceTest` — compra com sucesso, cashPoints insuficiente, item inativo, bloqueado pelo fraud
- `NpcShopControllerTest`, `CashShopControllerTest` — status codes

### Ownership de AccountEntity entre A1 e A3

**Agente A1 e somente A1 cria e e dono de `AccountEntity.java` e `AccountRepository.java`.**

Agente A3 NAO cria nenhuma versao de `AccountEntity`. Em vez disso:
- `CashShopService` recebe `accountId` (Long) como parametro e injeta `AccountRepository`
- `AccountRepository` e declarado como dependencia externa no Javadoc: `// REQUER: AccountRepository criado pelo Agente A1`
- Na integracao apos Onda 1, A3 injeta o `AccountRepository` ja existente sem conflito de arquivo

---

## Sistema 4 — Mercado entre jogadores (Agente B1)

### Decisoes de design

- Mercado publica listagens; qualquer player pode comprar
- Item permanece no inventario do seller ao listar (sem slot reservado) — validado no momento da compra
- FraudClient chamado em `createListing` e em `buy`
- Depende de Onda 1 (JWT) para validacao de ownership via `accountId`

### Nova entidade: MarketListingEntity

Tabela: `market_listings`

| Campo | Tipo Java | Coluna DB | Observação |
|-------|-----------|-----------|------------|
| `id` | Long | `id` PK auto | |
| `sellerPlayerId` | Long | `seller_player_id` FK players | |
| `playerItemId` | UUID | `player_item_id` FK player_items | |
| `itemId` | Long | `item_id` | copia para busca sem join |
| `priceZenny` | Long | `price_zenny` | |
| `quantity` | Integer | `quantity` | |
| `status` | String | `status` | ACTIVE / SOLD / CANCELLED |
| `listedAt` | Instant | `listed_at` | |
| `soldAt` | Instant | `sold_at` | nullable |

### Novos arquivos

```
infrastructure/persistence/
  MarketListingEntity.java
  MarketListingRepository.java   — findByStatus, findByItemIdAndStatus

application/service/
  MarketService.java

api/controller/
  MarketController.java
api/dto/request/
  CreateListingRequestDTO.java   — { sellerPlayerId, playerItemId, priceZenny, quantity }
  BuyListingRequestDTO.java      — { buyerPlayerId }
api/dto/response/
  MarketListingResponseDTO.java
```

### Endpoints

```
GET  /api/market/listings              — lista ACTIVE (?itemId= filtro opcional)
POST /api/market/listings              — cria listagem
POST /api/market/listings/{id}/buy     — body: { buyerPlayerId }
POST /api/market/listings/{id}/cancel  — body: { sellerPlayerId }
```

### Fluxo de listagem

```
MarketService.createListing(sellerPlayerId, playerItemId, priceZenny, quantity)
  1. valida seller possui playerItem e amount >= quantity
  2. fraudClient.checkItemTrade(sellerPlayerId, itemId, playerItemId.toString(), priceZenny)
     -> se BLOCKED: lanca GameException
  3. cria MarketListingEntity com status ACTIVE
  4. salva
```

### Fluxo de compra (atomic @Transactional)

```
MarketService.buy(listingId, buyerPlayerId)
  1. carrega listing; valida status == ACTIVE
  2. valida buyer != seller
  3. valida buyer.zenny >= listing.priceZenny
  4. valida seller ainda possui o item
  5. fraudClient.checkMarketPurchase(buyerPlayerId, itemId, quantity, totalActiveListings)
     -> se BLOCKED: lanca GameException
  6. transfere item: cria PlayerItemEntity para buyer (ou incrementa stack existente)
  7. decrementa/remove PlayerItemEntity do seller
  8. buyer.zenny -= priceZenny; seller.zenny += priceZenny
  9. listing.status = SOLD; listing.soldAt = Instant.now()
  10. salva tudo
```

### Testes obrigatorios

- `MarketServiceTest` — listar, criar com sucesso, criar sem item, compra com sucesso, compra sem zenny, compra com item sumido, cancelar
- `MarketControllerTest` — todos os endpoints

---

## Sistema 5 — Wiring do JWT (Agente B2)

### O que este agente faz

Registra o `JwtFilter` (criado por A1) e adiciona `validateOwnership` nos controllers. Nao cria logica de negocio.

### Novo arquivo

```
infrastructure/config/FilterConfig.java
```

```java
@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilter(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
```

### Rotas publicas (bypass no JwtFilter)

- `POST /api/accounts/register`
- `POST /api/accounts/login`

### Controllers a modificar

> Os controllers `PlayerController`, `BattleController`, `SkillController`, `ItemController` e `MapController`
> foram criados na Feature 4 (REST API) e ja existem no projeto. B2 os modifica para adicionar ownership validation.
> Os controllers `TradeController`, `NpcShopController`, `CashShopController` e `MarketController` sao criados
> em Onda 1/2 e ja nascem com ownership validation.

Padrao adotado em todos os controllers que recebem `playerId`:

```java
Long accountId = (Long) request.getAttribute("accountId");
accountService.validateOwnership(accountId, playerId);
```

| Controller | Status | Endpoints afetados |
|---|---|---|
| `PlayerController` | ja existe (Feature 4) — B2 modifica | todos |
| `BattleController` | ja existe (Feature 4) — B2 modifica | `POST /api/battle/attack` |
| `SkillController` | ja existe (Feature 4) — B2 modifica | todos |
| `ItemController` | ja existe (Feature 4) — B2 modifica | todos |
| `MapController` | ja existe (Feature 4) — B2 modifica | todos |
| `TradeController` | criado por A2 — ja nasce protegido | todos |
| `NpcShopController` | criado por A3 — ja nasce protegido | todos |
| `CashShopController` | criado por A3 — usa accountId do token, nao playerId | todos |
| `MarketController` | criado por B1 — ja nasce protegido | todos |

### Testes obrigatorios

- `JwtFilterIntegrationTest` — requisicao sem token retorna 401, token invalido retorna 401, rota publica passa sem token
- Ajustar mocks nos `*ControllerTest` existentes para incluir `accountId` no request attribute

---

## Resumo de novos arquivos por sistema

| Sistema | Entidades | Services | Controllers | Outros |
|---------|-----------|----------|-------------|--------|
| A1 Login | AccountEntity | AccountService | AccountController | JwtUtil, JwtFilter |
| A2 Trade | TradeOfferEntity | TradeService | TradeController | — |
| A3 Shop | CashShopItemEntity | NpcShopService, CashShopService | NpcShopController, CashShopController | — |
| B1 Market | MarketListingEntity | MarketService | MarketController | — |
| B2 Wiring | — | — | (modifica existentes) | FilterConfig |

**Total de novos arquivos estimados:** ~40 (entities, repos, services, controllers, DTOs, testes)

---

## Ordem de execucao sugerida para o plano de implementacao

```
Onda 1 (paralelo):
  A1: pom.xml -> AccountEntity -> AccountRepository -> JwtUtil -> JwtFilter
      -> AccountService -> AccountController -> DTOs -> Testes
  A2: TradeOfferEntity -> TradeOfferRepository -> TradeService
      -> TradeController -> DTOs -> Testes
  A3: CashShopItemEntity -> NpcShopService -> CashShopService
      -> NpcShopController -> CashShopController -> DTOs -> Testes

Onda 2 (apos Onda 1 — paralelo):
  B1: MarketListingEntity -> MarketListingRepository -> MarketService
      -> MarketController -> DTOs -> Testes
  B2: FilterConfig -> wiring nos controllers existentes -> ajuste testes
```
