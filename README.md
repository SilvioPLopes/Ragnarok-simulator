# Ragnarok Core — Hexagonal Architecture

![CI](https://github.com/SilvioPLopes/ragnarok-core/actions/workflows/ci.yml/badge.svg)
![Coverage](https://img.shields.io/badge/coverage-85%25%2B-brightgreen)

Este projeto é o núcleo (Core) de um sistema de emulação e gerenciamento de dados baseado no jogo **Ragnarok Online**. O projeto foi construído e refatorado para **Arquitetura Hexagonal (Ports and Adapters)**, desacoplando completamente as regras de negócio (Domínio) de frameworks externos, banco de dados e APIs. Toda a base de dados do jogo (monstros, itens, mapas, warps, drops, skills) é importada diretamente do servidor oficial **rAthena** (`db/re/` — versão Renewal).

---

## Status do Projeto

| Sistema | Status | Detalhe |
|---|---|---|
| Arquitetura Hexagonal | Operacional | Domain / Application / Infrastructure / Runner |
| Dados rAthena | Operacional | 2675 monstros, todos os itens, 1864 warps, 2374 spawns, 12544 drops |
| Navegação por Mapas | Operacional | Portais reais do rAthena, encontros ponderados por `amount` |
| Engine de Batalha | Operacional | Dano físico, contra-ataque do monstro, modificadores elemental e de tamanho |
| Sistema de Loot | Operacional | Taxas reais rAthena (0–100%), RNG, persistência no inventário |
| Sistema de Nível | Operacional | Base/Job XP, Level Up automático, Full Heal, stat/skill points |
| Distribuição de Stats | Operacional | Menu terminal para gastar `statPoints` (STR/AGI/VIT/INT/DEX/LUK) |
| Sistema de Skills | Operacional | Árvore completa, pré-requisitos AND-logic, aprendizado com `skillPoints` |
| Cadeia de Classes | Operacional | Skills de TODAS as classes anteriores visíveis e aprendíveis |
| Efeitos de Skills | Operacional | Buffs, passivas, dano com fórmulas, custo SP, duração em turnos |
| Modificadores de Tamanho | Operacional | Weapon vs. Small/Medium/Large com tabela real do jogo |
| Troca de Classe | Operacional | Progressão NOVICE → Tier1 → Tier2 → Tier3 com validação de job level |
| Startup Automático | Operacional | `StartupDataLoader` popula todas as tabelas estáticas no boot |
| Cobertura de Testes | **212 testes** | Unit + Integração — zero falhas (JaCoCo ≥ 85% instrução / ≥ 62% branch) |

---

## Arquitetura e Organização

O projeto segue estritamente a separação de responsabilidades da arquitetura hexagonal:

### 1. Domain (`com.ragnarok.domain`)

**O coração do sistema.** Contém a lógica de negócio e os modelos puros.

- **Regra de Ouro:** Sem dependências de frameworks. Sem `@Entity`, `@Table` ou qualquer anotação Spring/Hibernate.
- Usa tipos fortes (`Integer`, `Double`) para cálculos matemáticos seguros.
- `BattleEngine` e `LevelingService` são serviços puros — testáveis sem banco de dados.

### 2. Application (`com.ragnarok.application`)

**A camada de orquestração (Use Cases).** Recebe comandos, busca dados pelas portas e coordena o fluxo.

| Serviço | Responsabilidade |
|---|---|
| `BattleService` | Ciclo de combate, morte, loot e XP |
| `SkillService` | Listagem por cadeia de classes, validação de pré-requisitos, aprendizado e uso de skills |
| `ClassChangeService` | Progressão de classe com validação de job level |
| `ItemService` | Gestão de inventário, equip/unequip, auto-swap de slot |
| `PlayerService` | Criação e gerenciamento de personagens |
| `MonsterCatalogService` | ETL de monstros via API externa |
| `WeaponSizeService` | Consulta de modificadores de tamanho por tipo de arma e tamanho de monstro |
| `ScriptInterpreter` | Motor de avaliação de fórmulas de skills (`"ATK * skill_lv * 1.3"`) |

### 3. Infrastructure (`com.ragnarok.infrastructure`)

**Os adaptadores para o mundo externo.**

- **Client:** Comunicação com a API Ragnapi. Usa DTOs imutáveis (Records) com campos `String` para tolerar dados malformatados.
- **Persistence:** Comunicação com PostgreSQL via JPA. Entities com `@Entity` e estratégia de Flattening.
- **Mapper:** Ponte tradutora. Converte `DTO → Domain → Entity`, aplicando sanitização e safe unboxing.
- **`BuffSerializer`:** Serializa/desserializa a lista de `ActiveBuff` como JSON na coluna `active_buffs_json` do player.

### 4. Runner (`com.ragnarok.runner`)

| Classe | Ordem | Função |
|---|---|---|
| `RathenaImporter` | `@Order(1)` | Importa monstros e itens do rAthena GitHub automaticamente no startup |
| `PlayerSeedLoader` | `@Order(2)` | Cria o player inicial |
| `StartupDataLoader` | `@Order(3)` | Popula `maps`, `map_portals`, `map_monsters`, `monster_drops`, `skills`, `skill_tree`, `skill_buff_effects` e `weapon_size_modifiers` a partir dos SQLs em `src/main/resources/db/` |
| `RagnarokTerminalRunner` | — | UI do terminal, game loop de exploração e combate |

---

## Fluxos de Dados Implementados

### 1. Monster Catalog (ETL & Data Mining)

Fluxo resiliente que carrega dados externos, sanitiza inconsistências e realiza mineração de dados relacionais.

1. **Trigger:** `MonsterCatalogService.carregarESalvarMonstro(Long id)`
2. **Busca (Client):** `RagnapiClient` consome a API externa — input: JSON "sujo" (`"hp": "10,000"`, `"name": "scorpion"`)
3. **Sanitização (Mapper):** `MonsterMapper.toDomain(dto)` — remove vírgulas, normaliza strings, garante tipagem forte (`String → Integer`)
4. **Domínio:** Instanciação do objeto `Monster` puro para cálculos seguros
5. **Persistência (Mapper):** `MonsterMapper.toEntity(domain)` aplica **Flattening** (achata objetos aninhados em colunas planas)
6. **Mineração Automática:** Drops identificados; itens inexistentes viram placeholders. Spawns populam `maps` e `map_monsters`

### 2. Character Creation (Factory & Persistence)

1. **Trigger:** `PlayerService.criarNovoPersonagem(nome, classe)`
2. **Domínio:** Nível 1, HP 100/100, localização: Prontera
3. **Mapeamento:** `PlayerMapper` converte `PlayerStats`, `PlayerLocation` para colunas planas
4. **Persistência:** Commit transacional no PostgreSQL

### 3. Item Management (Inventory & Equipment)

1. **Inventário UUID:** Cada item tem UUID único em `player_items`, suportando múltiplas instâncias do mesmo item
2. **Auto-Swap:** `ItemService` identifica o `EquipSlot` do novo item, remove o item equipado no slot e equipa o novo em uma única transação atômica
3. **Loot Persistido:** Taxa de drop (`rate`) armazenada em escala 0–100 (normalizada a partir da escala rAthena 0–10000 na ingestão SQL)

### 4. Battle & Progression (Engine & Leveling)

1. **Trigger:** `BattleService.realizarAtaque(playerId, monsterId)`
2. **Combate (Engine):** `BattleEngine` calcula `(STR*2 + WeaponATK) - EnemyDEF`; aplica modificador elemental e de tamanho de arma
3. **Counter-ataque:** Monstro responde no mesmo turno com `ATK - PlayerDEF`
4. **Loot (RNG):** Na morte do monstro, `nextDouble(0, 100) < rate` determina cada drop; itens salvos em `player_items`
5. **XP & Level Up:** `LevelingService` processa base/job XP, verifica curva (`Nível * 100`), aplica Full Heal, +5 stat points, +1 skill point por level up

### 5. World Navigation (Map & Portals)

1. **Trigger:** Jogador seleciona "Portais" no menu de exploração
2. **Consulta:** `MapPortalRepository.findDestinosByMapFrom(mapaAtual)` retorna destinos disponíveis
3. **Viagem:** `PlayerEntity.mapName` atualizado e persistido
4. **Encontro:** Monstro sorteado por peso proporcional ao `amount` do spawn em `map_monsters`
5. **Morte:** Player revive em `prontera`, `mapName` resetado

### 6. Skills System (Aprendizado, Pré-requisitos e Cadeia de Classes)

1. **Trigger:** Menu de Status → tecla `S`
2. **Cadeia de Classes:** `SkillService.resolveClassChain` percorre o campo `parentClass` do enum `JobClass` e retorna todas as classes da progressão. Ex.: `LORD_KNIGHT → [LORD_KNIGHT, KNIGHT, SWORDSMAN]`
3. **Listagem:** `listarSkillsDoPlayer` busca a `skill_tree` para **toda a cadeia** — o player LORD_KNIGHT vê e pode aprender skills `LK_*`, `KN_*` e `SM_*`
4. **Pré-requisitos AND-logic:** Uma skill com múltiplas linhas na `skill_tree` exige **todos** os pré-requisitos satisfeitos
5. **Aprendizado:** `aprenderSkill` valida classe/cadeia, pré-requisitos, nível máximo e `skillPoints`. Faz upsert em `player_skills` e decrementa `skillPoints`
6. **Passivas imediatas:** Ao aprender uma skill `PASSIVE`, os bônus são aplicados instantaneamente via `ActiveBuff` com `durationTurns = -1` (permanente)

### 7. Skill Effects System (Buffs, Passivas e Dano)

1. **Fórmulas dinâmicas:** `ScriptInterpreter.evaluateFormula` avalia expressões como `"ATK * skill_lv * 1.3"` ou `"skill_lv * 2"` em runtime
2. **BUFF:** Ao usar a skill, os efeitos são lidos de `skill_buff_effects`, calculados e aplicados como `ActiveBuff` com duração. Resposta inclui os valores concretos: `"SM_ENDURE (Lv1). Efeito dura 7 turnos. [DEF +2, M_DEF +4]"`
3. **PASSIVE:** Aplicada ao aprender. Bônus permanente armazenado em `active_buffs_json` do player
4. **PHYSICAL_DAMAGE / MAGICAL_DAMAGE:** Fórmula avaliada com stats do player; modificador elemental e de tamanho de arma aplicados
5. **HEAL:** Fórmula avaliada para curar HP do player
6. **Modificador de Tamanho:** `WeaponSizeService` consulta `weapon_size_modifiers` para retornar o percentual de dano arma × tamanho do monstro (Small/Medium/Large)

### 8. Safety & Resilience (Null Safety)

1. **Mapper Blindado:** `PlayerMapper` implementa Safe Unboxing — `NULL` em campos numéricos (XP, Pontos, Zenny) é convertido para `0` antes de instanciar o Domínio, prevenindo `NullPointerException`
2. **Drop Rate Seguro:** `MonsterMapper.mapDrop` trata `rate = null` como `0.0` — item nunca dropa por acidente
3. **WHERE EXISTS duplo:** `monster_drops.sql` e `map_monsters.sql` usam `WHERE EXISTS` para validar FK antes de inserir, garantindo zero violações mesmo com banco parcialmente populado

---

## Banco de Dados

**URL:** `jdbc:postgresql://localhost:5432/ragnarok_db`
**User:** `postgres` / **Password:** *(variável de ambiente `DB_PASS`, padrão local: `postgre`)*

| Tabela | Origem | Descrição |
|---|---|---|
| `monsters` | RathenaImporter (startup) | 2675 monstros do `db/re/mob_db.yml` |
| `items` | RathenaImporter (startup) | Itens de `db/re/item_db_usable/equip/etc.yml` |
| `maps` | `maps.sql` | Todos os mapas do `db/map_index.txt` |
| `map_portals` | `map_portals_v2.sql` | 1864 warps de `npc/re/warps/` |
| `map_monsters` | `map_monsters.sql` | 2374 spawns de `npc/re/mobs/` com peso (`amount`) |
| `monster_drops` | `monster_drops.sql` | 12544 drops; `rate` em escala 0–100 (rAthena ÷ 100) |
| `players` | MockMapLoader (startup) | Jogador inicial |
| `player_items` | Gerado em combate | Inventário (UUID PK, `is_equipped`, `amount`) |
| `skills` | `skills.sql` + `forceLoad` | Catálogo: `aegis_name`, `name`, `effect_type`, `damage_formula`, `sp_cost`, `duration_turns` |
| `skill_tree` | `skill_tree.sql` | Árvore por classe: `job_class`, `skill_id`, `max_level`, `prereq_skill`, `prereq_level` |
| `skill_buff_effects` | `skill_effects.sql` | Efeitos de buff/passiva por skill: `stat_type`, `value_formula` |
| `player_skills` | JPA ddl-auto | Skills aprendidas: `player_id`, `skill_id`, `current_level` |
| `weapon_size_modifiers` | `weapon_size_modifiers.sql` | Modificadores de dano: `weapon_type`, `monster_size`, `modifier_percent` |
| `monster_spawns` | Legado | Substituído por `map_monsters` |

### Resetando dados do banco

```sql
-- Reimportar itens (força RathenaImporter no próximo startup):
DELETE FROM items;

-- Skills são sempre atualizadas via forceLoad — não precisam de DELETE manual.

-- Reset completo (respeitar ordem de FK):
DELETE FROM monster_drops;
DELETE FROM map_monsters;
DELETE FROM monster_spawns;
DELETE FROM monsters;
DELETE FROM items;
```

> Na próxima inicialização, `RathenaImporter` detecta `itemRepo.count() == 0` e reimporta automaticamente. O `StartupDataLoader` recarrega todos os SQLs estáticos.

### Startup automático

**Nenhum script manual necessário.** O `StartupDataLoader` (`@Order(3)`) popula todas as tabelas estáticas no boot se estiverem vazias. O console exibe o progresso:

```
Populando map_monsters    a partir de db/map_monsters.sql...
map_monsters    populada com 2374 registros.
```

> Os SQLs ficam em `src/main/resources/db/`. Os scripts Python em `scriptsPython/` são usados **apenas para regenerar** os SQLs quando os dados do rAthena mudam.

---

## Scripts Python (`scriptsPython/`)

Pipeline ETL que extrai dados diretamente dos repositórios do rAthena via GitHub e gera os SQLs para o banco.

| Script | Função |
|---|---|
| `Migrate.py` | Executa todos os SQLs no banco em ordem correta |
| `map_parser.py` | Gera `maps.sql` a partir de `db/map_index.txt` |
| `warp_parser.py` | Gera `map_portals_v2.sql` de `npc/re/warps/` |
| `mob_parser.py` | Gera `map_monsters.sql` de `npc/re/mobs/` |
| `drop_parser.py` | Gera `monster_drops.sql` de `db/re/mob_db.yml`; normaliza rate: `rAthena_rate / 100.0` |
| `skill_parser.py` | Gera `skill_tree.sql` de `db/re/skill_tree.txt` |

**Dependências Python:**
```bash
pip install requests pyyaml psycopg2-binary --break-system-packages
```

---

## Estrutura de Pastas

```text
com.ragnarok
├── application
│   └── service
│       ├── BattleService.java           # Ciclo de combate, loot, XP
│       ├── ClassChangeService.java      # Progressão de classe
│       ├── ItemService.java             # Inventário, equip, auto-swap
│       ├── MonsterCatalogService.java   # ETL monstros via API
│       ├── PlayerService.java           # Criação e gestão de personagens
│       ├── ScriptInterpreter.java       # Motor de fórmulas: "ATK * skill_lv * 1.3"
│       ├── SkillRowDTO.java             # Record público para exibição de skill no terminal
│       ├── SkillService.java            # Listagem (cadeia de classes), aprendizado, uso
│       └── WeaponSizeService.java       # Modificador de dano: weapon_type × monster_size
│
├── domain
│   ├── model
│   │   ├── ActiveBuff.java             # Buff ativo (skill, stat, valor, turnos restantes)
│   │   ├── BattleResult.java
│   │   ├── BuffFlag.java
│   │   ├── EffectResult.java           # Resultado de avaliação de script legado
│   │   ├── ElementalDamage.java
│   │   ├── EquipSlot.java
│   │   ├── Item.java
│   │   ├── ItemDropInfo.java
│   │   ├── ItemStats.java              # Value Object de stats de equipamento
│   │   ├── ItemType.java
│   │   ├── JobClass.java               # Enum completo com tier, parentClass e stats base
│   │   ├── MainAttributes.java
│   │   ├── MainStats.java
│   │   ├── Monster.java
│   │   ├── MonsterDrop.java
│   │   ├── Player.java
│   │   ├── PlayerItem.java
│   │   ├── PlayerLocation.java
│   │   ├── PlayerStats.java
│   │   ├── SkillEffectType.java        # Enum: PHYSICAL_DAMAGE, MAGICAL_DAMAGE, BUFF, HEAL, PASSIVE
│   │   ├── SkillElement.java           # Enum: NEUTRAL, FIRE, WATER, WIND, EARTH, HOLY, SHADOW...
│   │   ├── StatType.java               # Enum: STR, AGI, VIT, INT, DEX, LUK, DEF, M_DEF, FLEE...
│   │   └── WeaponType.java             # Enum: SWORD, DAGGER, SPEAR, BOW, STAFF, MACE, NONE...
│   └── service
│       ├── BattleEngine.java           # Dano, loot RNG, modificadores — zero dependência de banco
│       └── LevelingService.java        # Curva de XP, Level Up, recompensas
│
└── infrastructure
    ├── client
    │   ├── RagnapiClient.java
    │   ├── dto
    │   │   ├── ItemDTO.java
    │   │   └── MonsterDTO.java          # Record imutável, campos String para tolerar dados sujos
    │   └── mapper
    │       ├── ItemMapper.java
    │       ├── MonsterMapper.java       # Sanitização + rate de drop sem divisão (banco já normalizado)
    │       └── PlayerMapper.java        # Safe Unboxing (NULL → 0)
    │
    └── persistence
        ├── GameMapEntity.java / Repository
        ├── ItemEntity.java / Repository
        ├── MapMonsterEntity.java / Repository    # Spawns com amount (peso ponderado)
        ├── MapPortalEntity.java / Repository
        ├── MonsterDropEntity.java
        ├── MonsterEntity.java / Repository
        ├── MonsterSpawnEntity.java / Repository  # Legado
        ├── PlayerEntity.java / Repository
        ├── PlayerItemEntity.java / Repository    # UUID PK, is_equipped, amount
        ├── PlayerSkillEntity.java / Repository
        ├── SkillBuffEffectEntity.java / Repository  # stat_type + value_formula por skill
        ├── SkillEntity.java / Repository
        ├── SkillTreeEntity.java / Repository     # findByJobClassesIn (cadeia de classes)
        ├── WeaponSizeModifierEntity.java / Repository
        └── mapper
            ├── BuffSerializer.java               # JSON ↔ List<ActiveBuff>
            └── PlayerMapper.java
```

---

## Dicionário de Classes Chave

### Domain Models

| Classe | Descrição |
|---|---|
| `Monster` | Modelo rico com `MainStats`, `ElementalDamage`, lista de `MonsterDrop` |
| `Player` | Modelo rico com inventário, `statPoints`, `skillPoints`, `xp`, `mapName`, `activeBuffs` |
| `Item` | Modelo puro com `ItemStats` (Value Object) e `WeaponType` |
| `MonsterDrop` | Associa `Item` a `rate` (Double, escala 0–100) |
| `ActiveBuff` | Buff ativo: skill de origem, `StatType`, valor e `durationTurns` (-1 = permanente) |
| `JobClass` | Enum com todos os jobs (Tier 0–4), `parentClass` para cadeia de progressão e stats base por classe |

### Domain Services

| Classe | Descrição |
|---|---|
| `BattleEngine` | Fórmula de dano `(STR*2 + WeaponATK) - DEF`, RNG de loot `nextDouble(0,100) < rate`, modificador elemental, modificador de tamanho |
| `LevelingService` | Curva `Nível * 100`, Level Up Base e Job, Full Heal, distribuição de stat/skill points, caps por `JobClass` |

### Infrastructure Entities

| Entidade | Detalhe |
|---|---|
| `PlayerEntity` | Flattening: `base_exp`, `job_exp`, `stat_points`, `skill_points`, `map_name`, `active_buffs_json` |
| `MonsterDropEntity` | `rate` como `DOUBLE PRECISION` em escala 0–100; FK para `monsters` e `items` |
| `SkillTreeEntity` | Read-only; múltiplas linhas por skill = múltiplos pré-requisitos |
| `SkillBuffEffectEntity` | `skill_id` + `stat_type` + `value_formula` — avaliada em runtime pelo `ScriptInterpreter` |
| `WeaponSizeModifierEntity` | `weapon_type` + `monster_size` + `modifier_percent` |
| `PlayerItemEntity` | UUID PK, permite múltiplas instâncias do mesmo item (ex: duas katanas com refinos diferentes) |
| `MapMonsterEntity` | `map_id` + `monster_id` + `amount` — sorteio ponderado por `amount` |

---

## Cobertura de Testes

**195 testes — 0 falhas — BUILD SUCCESS**

> Executar com Java 17: `JAVA_HOME=/path/to/jdk-17 ./mvnw test`
> (Java 21+ quebra o Mockito inline-mock-maker sem configuração adicional de `--add-opens`)

### Unit Tests

| Classe | Testes | Cobertura |
|---|---|---|
| `BattleEngineTest` | 20 | Fórmula de dano, dano mínimo 1, DEF=0, loot 100%/0%/50%, counter-attack, rate escala, null guards |
| `LevelingServiceTest` | 7 | Curva de XP, Level Up, reset de XP excedente, Full Heal, stat/skill points |
| `PlayerTest` | 38 | Inventário, getTotalDef, getWeaponAtk, activeBuffs, equipamento |
| `ActiveBuffTest` | 4 | Construção, expiração, permanente (`durationTurns = -1`) |
| `MonsterMapperTest` | 9 | Rate 100/70/0.35/0.0/null, item não nulo, invariante ≤100, drops vazios/null |
| `ScriptInterpreterTest` | 16 | Fórmulas aritméticas, variáveis de stats, casos extremos |
| `WeaponSizeServiceTest` | 6 | Modificadores por tipo de arma e tamanho de monstro |
| `ClassChangeServiceTest` | 14 | `listarClassesDisponiveis`, `trocarClasse` com mocks de repositório |
| `BattleServiceTest` | 10 | Ataque normal, morte (VITÓRIA), counter-attack, morte do jogador (FATAL), null guards |

### Integration Tests

| Classe | Testes | Cobertura |
|---|---|---|
| `BattleIntegrationTest` | 2 | Dano físico e counter-attack no banco real |
| `BattleLootIntegrationTest` | 1 | Drop RNG e persistência em `player_items` |
| `SkillServiceIntegrationTest` | 10 | Listagem, skills disponíveis, HEAL, BUFF, SP insuficiente, PASSIVE, PHYSICAL_DAMAGE, BUFF sem efeitos |
| `SkillServiceAprenderTest` | 7 | Incremento de nível, decremento de skillPoints, nível máximo, skill inexistente, jobClass nula |
| `ClassChangeIntegrationTest` | 2 | `trocarClasse` persiste `jobClass/jobLevel/jobExp`; listagem para NOVICE |
| `ItemServiceIntegrationTest` | 15 | CRUD de inventário, equip/unequip, auto-swap de slot |
| `StartupDataLoaderSqlTest` | 9 | `monster_drops.sql` e `map_monsters.sql`: FK safety, idempotência, WHERE EXISTS |
| `PlayerInventoryIntegrationTest` | 2 | Equip e unequip no banco real |
| `InventoryDebugTest` | 3 | Integridade do inventário |
| `MapSpawnIntegrationTest` | 1 | Sorteio ponderado por `amount` |
| `MonsterDropIntegrationTest` | 1 | Leitura de drops do banco |
| `RagnarokTerminalRunnerTest` | 11 | Ressurreição automática, flows de exploração |
| `MonsterCatalogServiceTest` | 1 | ETL completo API → banco |
| `ItemLoadingTest` | 1 | Carregamento de itens |

---

## Roadmap

### Concluído

- **Cadeia de Classes para Skills:** LORD_KNIGHT vê e aprende skills de Knight e Swordsman
- **Efeitos de Skills:** Buffs com `stat_type` + `value_formula`, passivas permanentes, dano com fórmulas dinâmicas
- **Modificadores de Tamanho:** `WeaponSizeService` + tabela `weapon_size_modifiers`
- **Motor de Fórmulas:** `ScriptInterpreter` avalia expressões matemáticas com variáveis de stats em runtime
- **Mensagens de Efeito:** Usar skill mostra efeitos concretos: `[DEF +2, M_DEF +4]`
- **Fix Drop Rate:** Escala normalizada para 0–100 na ingestão SQL (`rAthena ÷ 100`)
- **Menu de Distribuição de Stats:** Terminal permite gastar `statPoints` nos 6 atributos
- **Sistema de Skills completo:** `skill_tree`/`player_skills`, listagem, pré-requisitos, aprendizado
- **Troca de Classe:** `ClassChangeService`, progressão NOVICE→Tier1→Tier2, menu via `C`
- **Startup automático:** `StartupDataLoader` — zero scripts manuais
- **Limpeza de tela:** `clearScreen()` com ANSI codes entre menus
- **Inventário empilha corretamente:** Drop de item existente incrementa `amount`
- **Stat/skill points acumulam em multi-levelup**
- **LevelingService:** Caps de nível base/job por classe via `JobClass.maxJobLevel()`

### Backlog

#### Alta Prioridade

1. **Usar skills em batalha (terminal)** — `usarSkillEmCombate` existe no `SkillService`; integrar ao menu de combate do terminal com seleção de skill e alvo
2. **Usar itens em batalha** — adicionar opção "Item" no menu de combate para consumíveis do inventário
3. **Mecânicas dos stats faltantes:**
   - **AGI** → FLEE (evasão) e ASPD (velocidade de ataque)
   - **DEX** → HIT (precisão) e redução de cast time
   - **LUK** → taxa de crítico e bônus de drop rate
4. **Butterfly Wing / Fly Wing** — teleporte para Prontera / ponto aleatório do mapa

#### Média Prioridade

5. **Enciclopédia de mapas** — mapas vizinhos, monstros do mapa, drops com raridade
6. **Sistema de lojas NPC** — compra/venda com Zenny em cidades
7. **Troca de classe restrita a NPCs** — permitir apenas em locais específicos

#### Futuro

- **Persistência de posição (X,Y)** — salvar coordenadas ao sair
- **Conexões de borda entre mapas** — campos conectados geograficamente sem NPC warp
- **Multiplayer (WebSockets)** — longo prazo

---

## Build & Run

```bash
# Build
./mvnw clean install

# Rodar a aplicação
./mvnw spring-boot:run

# Rodar todos os testes (Java 17 recomendado)
JAVA_HOME=/path/to/jdk-17 ./mvnw test

# Rodar teste específico
./mvnw test -Dtest=BattleEngineTest
```

**Pré-requisitos:**
- Java 17+
- PostgreSQL rodando em `localhost:5432` com banco `ragnarok_db`
- Variável de ambiente `DB_PASS` com a senha do PostgreSQL (padrão local: `postgre`)

> No primeiro startup, o `RathenaImporter` baixa os dados do rAthena via GitHub (~2675 monstros + todos os itens). O `StartupDataLoader` popula o restante. A aplicação fica pronta em ~30–60 segundos dependendo da conexão.
