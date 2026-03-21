# Test Coverage Refactor — Design Spec
**Data:** 2026-03-18
**Projeto:** Ragnarok-simulator
**Meta:** 90%+ cobertura de linhas / 85%+ branches nas classes com lógica real

---

## Contexto

O projeto tem 90 testes passando em ~16s, mas cobertura geral de 51.9% de linhas e 32.6% de branches. A baixa cobertura está concentrada em:

| Pacote | Cobertura atual |
|--------|----------------|
| `runner` | 25.2% (366 linhas descobertas — maioria menus de terminal) |
| `runner/importer` | 1.2% (intencional: `@Profile("!test")`) |
| `application/service` | 61.6% (269 linhas descobertas) |
| `domain/model` | 68.3% (78 linhas descobertas) |
| `infrastructure/persistence` | 38.5% |

---

## Abordagem Escolhida: C — Híbrido

Excluir do JaCoCo classes sem valor de teste real. Cobrir com testes todas as classes com comportamento.

---

## Seção 1 — Exclusões do JaCoCo

Configurar `pom.xml` (plugin jacoco-maven-plugin) com `<excludes>` dentro da execution `report`:

```xml
<execution>
    <id>report</id>
    <phase>test</phase>
    <goals><goal>report</goal></goals>
    <configuration>
        <excludes>
            <exclude>com/ragnarok/runner/importer/**</exclude>
            <exclude>com/ragnarok/infrastructure/client/dto/**</exclude>
            <exclude>com/ragnarok/runner/importer/dto/**</exclude>
        </excludes>
    </configuration>
</execution>
```

Adicionar também uma execution `check` para enforcement automático no build:

```xml
<execution>
    <id>check</id>
    <phase>test</phase>
    <goals><goal>check</goal></goals>
    <configuration>
        <excludes>
            <exclude>com/ragnarok/runner/importer/**</exclude>
            <exclude>com/ragnarok/infrastructure/client/dto/**</exclude>
            <exclude>com/ragnarok/runner/importer/dto/**</exclude>
        </excludes>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.90</minimum>
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.85</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

**Motivo de cada exclusão:**
- `runner/importer/**` — `RathenaImporter` tem `@Profile("!test")`, baixa YAML de 50MB do GitHub. Testar seria mockar a internet. Sem valor.
- `infrastructure/client/dto/**` — Records/classes de apenas campos para deserialização da API externa. Zero lógica.
- `runner/importer/dto/**` — Mesma razão: DTOs de parsing sem comportamento.

Métodos `render*` em `RagnarokTerminalRunner` não serão excluídos via config — serão cobertos via testes unitários dos métodos de lógica que os suportam (sem simular menus completos).

---

## Seção 2 — Grupos de Testes (5 agentes paralelos)

### Grupo 1: Novos testes de domínio
**Arquivo novo:** `WeaponSizeServiceTest.java`
**Arquivo novo:** `ActiveBuffTest.java`
**Arquivo refatorado:** modelos de domínio sem cobertura

- `WeaponSizeService.getModifier(WeaponType, String)` — é `@Service` com `WeaponSizeModifierRepository` injetado (JPA). Testar com mock do repositório. Tamanhos válidos no código: `SMALL`, `LARGE` e `default` (cobre `MEDIUM` e qualquer outro). Sem `BOSS` — não criar esse caso.
- `ActiveBuff` — construtor, getters, comportamento de buff ativo. Puro JUnit sem mock.
- Domain models com lógica real ainda descoberta (ex: métodos de `Player`, `Monster`, `Item` com lógica)

**Tipo de teste:** `@ExtendWith(MockitoExtension.class)` com `@Mock WeaponSizeModifierRepository` para `WeaponSizeService`; puro JUnit para `ActiveBuff` e domain models.

---

### Grupo 2: BattleService — branches descobertos
**Arquivo refatorado:** `BattleServiceTest.java`

Branches a cobrir:
- Player morto antes de atacar (HP = 0) — lança `IllegalStateException`
- Loot com múltiplos drops (alguns dropam, outros não)
- XP suficiente para level up durante batalha
- Monstro sem `WeaponType` definido (weapon null)
- Cenário onde `applyWeaponSizeModifier` retorna dano mínimo (1)

**Tipo de teste:** `@ExtendWith(MockitoExtension.class)` — puro mock, sem banco.

---

### Grupo 3: SkillService + ClassChangeService — branches
**Arquivos refatorados:** `SkillServiceAprenderTest.java`, `SkillServiceIntegrationTest.java`, `ClassChangeServiceTest.java`

Branches a cobrir:
- `SkillService`: aprenderSkill com prereq não satisfeito, skill inexistente, player com jobClass nula
- `ClassChangeService`: todos os caminhos de validação — jobLevel insuficiente, classe não disponível no banco, progressão inválida
- `ClassChangeIntegrationTest`: trocar para cada classe tier-1 diferente

**Tipo de teste:** integração com `@SpringBootTest @ActiveProfiles("test")` para SkillService; puro mock para ClassChangeService.

---

### Grupo 4: ItemService + PlayerService — branches
**Arquivos refatorados:** `ItemServiceIntegrationTest.java`, `PlayerServiceTest.java`, `ItemServiceTest.java`

Branches a cobrir:
- `ItemService`: dar item a player inexistente, equipar item de tipo inválido, slot já ocupado por item diferente
- `PlayerService`: ressuscitar player já vivo (deve lançar exceção ou ser no-op), criar player com dados inválidos
- `ItemService.criarItemDeTeste`: verificar flattening completo dos campos de stats

**Tipo de teste:** integração com `@SpringBootTest @ActiveProfiles("test")`.

---

### Grupo 5: RagnarokTerminalRunner — lógica não-menu
**Arquivo refatorado:** `RagnarokTerminalRunnerTest.java`

Métodos a cobrir (sem simular menus):
- `iniciarEncontroAleatorio()` — encontro com monstro aleatório do mapa atual (mock `MapMonsterRepository`)
- `moverParaMapa(String)` — mudança de mapa, update no player (mock `PlayerRepository`)
- `handlePlayerDeath()` — ressuscita player, reseta mapa para prontera
- `caminhar(String mapaAtual)` — **obrigatório**; tem branch: 70% chama `iniciarEncontroAleatorio`, 30% não encontra monstro. Testar ambos os caminhos. Método recebe `String` como argumento — usar `ReflectionTestUtils.invokeMethod(runner, "caminhar", "prontera")`.

**Técnica:** `ReflectionTestUtils.invokeMethod()` + `@Mock` para todos os repositórios. Sem `ByteArrayInputStream` de menus completos.

---

## Seção 3 — Critérios de Sucesso

| Critério | Meta |
|----------|------|
| `./mvnw test` passa | 100% (0 falhas) |
| Cobertura linhas (excluindo DTOs/importer) | ≥ 90% |
| Cobertura branches (excluindo DTOs/importer) | ≥ 85% |
| Nenhum teste toca `ragnarok_db` | ✅ |
| Nenhum teste faz `findAll()` em tabela > 100 registros | ✅ |
| Tempo total do `./mvnw test` | < 60s |

---

## Seção 4 — Restrições

- Testes de integração (`@SpringBootTest`) DEVEM ter `@ActiveProfiles("test")`
- Testes que criam dados próprios DEVEM usar `@Transactional` ou cleanup manual em `@AfterEach`
- Nunca depender de dados pré-existentes no banco (exceto player ID=1 criado pelo MockMapLoader)
- Sem mocks de banco — testes de integração usam banco real `ragnarok_test`
- Sem testes de getter/setter em DTOs ou models sem lógica

---

## Ordem de Execução

1. Aplicar exclusões do JaCoCo no `pom.xml`
2. Grupos 1–5 em paralelo (arquivos independentes)
3. Rodar `./mvnw test` e verificar cobertura
4. Ajustar se necessário
