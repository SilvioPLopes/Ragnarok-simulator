# Documento de Contexto — ragnarok-core feature/Alt-05

**Leia este arquivo no início da sessão para retomar o contexto.**

---

## O que foi feito nesta branch

Branch: `feature/Alt-05`
Base: `main`
Status: implementação completa, 77 testes unitários passando.

Foram implementadas 5 features de portfolio sobre o projeto Spring Boot 3.4.2 / Java 17 de RPG inspirado em Ragnarok Online.

---

## Feature 1: Resilience4j — Retry + Circuit Breaker

**Objetivo:** Proteger o download de dados do rAthena (GitHub) contra falhas de rede com retry exponencial e circuit breaker.

**O que foi criado/modificado:**
- `pom.xml` — adicionado `resilience4j-spring-boot3` e `spring-boot-starter-aop`
- `application.properties` — 7 propriedades de configuração (max-attempts=3, wait=2s, backoff x2, circuit breaker sliding-window=3)
- `RathenaDownloadService.java` — novo `@Service @Profile("!test")` com `@Retry` e `@CircuitBreaker`, fallback retorna null com log WARN
- `RathenaImporter.java` — removido download direto, injeta `RathenaDownloadService`, checa null antes de importar

**Por que separou em bean diferente:** Resilience4j usa AOP proxy. Uma classe só pode interceptar chamadas vindas de fora dela mesma — o bean precisa ser diferente de quem o chama.

**Como testar com a aplicação rodando:**
1. Inicie a aplicação normalmente — no log deve aparecer:
   ```
   Importing monsters from rAthena...
   Imported 2675 monsters.
   ```
2. Para testar o fallback: temporariamente mude a URL em `RathenaDownloadService` para um host inválido, suba a aplicação e verifique:
   ```
   WARN RathenaDownloadService - rAthena unavailable after retries. Starting with existing data.
   WARN RathenaImporter - Monster import skipped — download returned null.
   ```

---

## Feature 2: Testcontainers — PostgreSQL em testes

**Objetivo:** Eliminar dependência de PostgreSQL local para rodar `./mvnw test`. Agora funciona em qualquer máquina com Docker.

**O que foi criado/modificado:**
- `pom.xml` — adicionado `testcontainers:postgresql` e `testcontainers:junit-jupiter` (scope test)
- `AbstractIntegrationTest.java` — classe base abstrata com `@Testcontainers`, `static PostgreSQLContainer<>`, `@DynamicPropertySource` injetando URL/user/password
- `application-test.properties` — removidas as 3 linhas de datasource hardcoded; comentário explica que vêm do `@DynamicPropertySource`
- 8 classes `*IntegrationTest.java` — removidos `@SpringBootTest`, `@ActiveProfiles`, `@MockBean RagnarokTerminalRunner`; adicionado `extends AbstractIntegrationTest`

**Como testar com Docker:**
```bash
# Com Docker Desktop aberto:
./mvnw test -q
# Primeira execução: ~30s para pull da imagem postgres:16
# Execuções seguintes: mais rápido
# Esperado: BUILD SUCCESS, todos os testes passando
```

Se Docker não estiver rodando, você verá:
```
Could not find a valid Docker environment
```

---

## Feature 3: Spring Cache — Caffeine

**Objetivo:** Cachear dados estáticos de jogo (modificadores de tamanho, efeitos de buff, skill tree, skills do player) para eliminar queries repetidas no banco durante batalhas.

**O que foi criado/modificado:**
- `pom.xml` — adicionado `spring-boot-starter-cache` + `caffeine`
- `RagnarokCoreApplication.java` — adicionado `@EnableCaching`
- `CacheConfig.java` — `SimpleCacheManager` com 4 `CaffeineCache` nomeados:
  - `weaponSizeModifiers` — sem TTL, até 1000 entradas
  - `skillBuffEffects` — sem TTL, até 1000 entradas
  - `skillTree` — sem TTL, até 100 entradas
  - `playerSkills` — TTL 5 minutos, até 500 entradas
- `WeaponSizeService.getModifier()` — `@Cacheable("weaponSizeModifiers")`
- `SkillService.listarSkillsDoPlayer()` — `@Cacheable(value="playerSkills", key="#playerId")`
- `SkillService.aprenderSkill()` — `@CacheEvict(value="playerSkills", key="#playerId")`
- `SkillTreeRepository.findByJobClassesIn()` — `@Cacheable("skillTree")`
- `SkillBuffEffectRepository.findBySkillId()` — `@Cacheable("skillBuffEffects")`
- `CacheVerificationTest.java` — usa `@SpyBean` para verificar que `findByWeaponType` é chamado exatamente 1 vez em 2 invocações consecutivas

**Como testar com Docker:**
```bash
./mvnw test -Dtest=CacheVerificationTest -q
# Esperado: Tests run: 2, Failures: 0, Errors: 0
```

---

## Feature 4: REST API + Swagger UI

**Objetivo:** Expor o loop completo do jogo como API REST com Swagger UI, permitindo jogar via browser sem clonar o repo.

**Estrutura criada:**
```
src/main/java/com/ragnarok/
  api/
    GlobalExceptionHandler.java       — @RestControllerAdvice
    dto/
      request/  — AttackRequestDTO, CreatePlayerRequestDTO, TravelRequestDTO, UseSkillRequestDTO
      response/ — PlayerResponseDTO, BattleResponseDTO, SkillRowResponseDTO, SkillUseResponseDTO,
                  InventoryItemResponseDTO, MapInfoResponseDTO, WalkResponseDTO
    controller/
      PlayerController.java   — GET /api/players, GET /api/players/{id}, POST /api/players
      BattleController.java   — POST /api/battle/attack
      SkillController.java    — GET /api/players/{id}/skills, POST .../learn, POST .../use
      ItemController.java     — GET /api/players/{id}/inventory, POST .../inventory/{itemId}/use
      MapController.java      — GET /api/players/{id}/map, GET /api/maps/{mapId}/portals,
                                POST /api/players/{id}/map/walk, POST /api/players/{id}/map/travel
  application/
    service/
      MapService.java         — getCurrentMap, getPortals, travel, walk (retorna WalkResult)
    dto/
      WalkResult.java         — record no application layer (não vaza DTO de API para service)
```

**Mapeamento de exceções (GlobalExceptionHandler):**
- `SkillNotFoundException` → 404
- `IllegalArgumentException` → 404
- `GameException` e subclasses (PlayerDeadException, InsufficientSpException, etc.) → 400
- `Exception` genérica → 500

**Como testar com Swagger UI aberto:**
1. Suba a aplicação: `./mvnw spring-boot:run` ou execute o JAR
2. Acesse: `http://localhost:8080/swagger-ui.html`
3. Você verá 5 grupos: **Players**, **Battle**, **Skills**, **Inventory**, **Map**

**Fluxo de teste via Swagger (ordem recomendada):**
```
1. POST /api/players          — cria personagem {"name":"Teste","jobClass":"NOVICE"}
   -> anote o "id" retornado (ex: 1)

2. GET /api/players/1         — verifica HP, level, zenny

3. POST /api/players/1/map/walk
   -> se encounterOccurred=true, anote o monsterId (ex: 1002)

4. POST /api/battle/attack    — {"playerId":1,"monsterId":1002}
   -> repita até aparecer "VITÓRIA"

5. GET /api/players/1/skills  — lista skills disponíveis

6. POST /api/players/1/skills/SM_BASH/learn
   -> aprende Bash

7. GET /api/players/1/inventory
   -> verifica itens dropados

8. POST /api/players/1/map/travel
   — {"destination":"izlude"}  (ou outro mapa da lista de portais)

9. GET /api/maps/prontera/portals
   -> lista portais disponíveis de prontera
```

**Testes unitários (sem Docker):**
```bash
./mvnw test -Dtest="PlayerControllerTest,BattleControllerTest,SkillControllerTest,ItemControllerTest,MapControllerTest,GlobalExceptionHandlerTest" -Djacoco.skip=true -q
# Esperado: 22 testes, 0 falhas
```

---

## Feature 5: Spring Events — Desacoplamento do BattleService

**Objetivo:** Desacoplar `BattleService` de persistência de loot, XP e morte do player usando eventos de domínio Spring.

**Antes:** `BattleService.realizarAtaque()` fazia tudo: calculava dano, persistia loot, processava XP, detectava level-up, ressuscitava player.

**Depois:** `BattleService` apenas calcula o turno e publica eventos. Tudo mais fica em `BattleEventHandler`.

**O que foi criado/modificado:**
```
domain/event/
  MonsterKilledEvent.java  — record(playerId, monsterId, List<Item> loot, baseExp, jobExp)
  PlayerLeveledUpEvent.java — record(playerId, newBaseLevel, newJobLevel)
  PlayerDiedEvent.java     — record(playerId)

application/service/
  BattleEventHandler.java  — @Component com dois @EventListener @Transactional:
    onMonsterKilled() — persiste loot, processa XP via LevelingService, publica PlayerLeveledUpEvent se subiu level
    onPlayerDied()   — seta HP=max, mapName=prontera, salva player

BattleService.java         — removido processarMorteMonstro(), adicionado ApplicationEventPublisher
                             publica MonsterKilledEvent e PlayerDiedEvent

RagnarokTerminalRunner.handlePlayerDeath() — simplificado: apenas reseta estado in-memory
                             (inBattle=false, currentMonster=null)
```

**Fluxo de evento ao matar monstro:**
```
realizarAtaque()
  └─ monsterHp <= 0
       └─ battleEngine.calculateLoot(monster)
       └─ eventPublisher.publishEvent(MonsterKilledEvent)  [síncrono, mesma transação]
            └─ BattleEventHandler.onMonsterKilled()
                 ├─ persiste loot no player_items
                 ├─ levelingService.processarExperiencia()
                 ├─ salva PlayerEntity com novos stats
                 └─ se subiu level → publishEvent(PlayerLeveledUpEvent)
```

**Como verificar via Swagger:**
1. Faça uma batalha completa até matar um monstro
2. `GET /api/players/{id}` — verifique que `baseLevel` e `jobLevel` foram atualizados
3. `GET /api/players/{id}/inventory` — verifique que itens dropados aparecem

**Testes unitários:**
```bash
./mvnw test -Dtest="BattleServiceTest,BattleEventHandlerTest" -Djacoco.skip=true -q
# Esperado: 14 testes, 0 falhas
```

---

## Rodar tudo de uma vez

**Apenas testes unitários (sem Docker):**
```bash
./mvnw test -Djacoco.skip=true -Dtest="BattleServiceTest,BattleEventHandlerTest,RagnarokTerminalRunnerTest,GlobalExceptionHandlerTest,PlayerControllerTest,BattleControllerTest,SkillControllerTest,ItemControllerTest,MapControllerTest,WeaponSizeServiceTest,SkillCombatServiceTest,ItemServiceTest,ClassChangeServiceTest" -q
# Esperado: ~77 testes, 0 falhas, BUILD SUCCESS
```

**Suite completa com Docker:**
```bash
./mvnw test -q
# Esperado: BUILD SUCCESS, todos os testes passando incluindo integração
```

---

## Estado do repositório

```
Branch: feature/Alt-05
Último commit: b0e64a8 test(events): improve BattleEventHandlerTest

Commits desta sessão (mais recentes primeiro):
b0e64a8  test(events): improve BattleEventHandlerTest
b8316fc  fix(events): @Transactional on BattleEventHandler listeners
af07a4c  refactor: simplify RagnarokTerminalRunner.handlePlayerDeath()
8270331  feat(events): BattleService publishes MonsterKilledEvent and PlayerDiedEvent
d297d94  feat(events): add BattleEventHandler with @EventListener
2a8cbf0  feat(events): add domain event records
ad550c7  fix(api): ItemController base path, MapService @Transactional, exception handler tests
a9654ec  fix(api): fix MapService layering (WalkResult in application layer)
293c984  feat(api): add ItemController and MapController — REST API complete
afc7af4  feat(api): add SkillController
2308432  feat(api): add BattleController
9357c15  feat(api): add PlayerController
7efb532  feat(api): add GlobalExceptionHandler
2eb26aa  feat(api): add DTOs and MapService
b086356  feat(api): add springdoc dependency and extend services
de686e0  test(cache): add CacheVerificationTest
8a0f100  feat(cache): annotate services with @Cacheable and @CacheEvict
6307c32  feat(cache): add CacheConfig with Caffeine named caches
d569f68  build: add Caffeine and enable Spring Cache
7003f05  test: migrate all integration tests to Testcontainers
b4cfb11  test: remove hardcoded datasource from application-test.properties
b83122f  test: add AbstractIntegrationTest with Testcontainers PostgreSQL
be0da75  build: add testcontainers dependencies
3610d38  feat(resilience4j): RathenaImporter delegates to RathenaDownloadService
49f4fba  feat(resilience4j): add RathenaDownloadService with @Retry and @CircuitBreaker
d16bcf8  config: add Resilience4j retry and circuit breaker config
7bcda4a  build: add resilience4j-spring-boot3 and spring-boot-starter-aop
```

---

## Próximos passos sugeridos

Após verificar tudo funcionando com Docker e Swagger:
1. `git checkout main && git merge feature/Alt-05` — integrar na main
2. Atualizar README com os novos endpoints e instruções do Swagger
3. Considerar adicionar `docker-compose.yml` com health check para facilitar onboarding
