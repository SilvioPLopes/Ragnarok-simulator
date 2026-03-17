# Ragnarok Core - Hexagonal Architecture Refactoring

Este projeto é o núcleo (Core) de um sistema de emulação e gerenciamento de dados baseado no jogo Ragnarok Online. O projeto está passando por uma refatoração profunda para **Arquitetura Hexagonal (Ports and Adapters)**, visando desacoplar as regras de negócio (Domínio) de frameworks externos, banco de dados e APIs. Toda a base de dados do jogo (monstros, itens, mapas, warps, drops) é importada diretamente do servidor oficial **rAthena** (`db/re/` — versão Renewal).

---

## ✅ Status do Projeto

- **Arquitetura:** Hexagonal (Domain, Application, Infrastructure).
- **Dados do jogo:** Importados do rAthena `db/re/` — 2675 monstros, todos os itens, 1864 warps, 2374 spawns, 12544 drops.
- **Navegação:** Player se move entre mapas reais usando portais do rAthena.
- **Engine de Batalha:** Cálculo de dano físico (Status + Equipamentos + Defesa), counter-ataque do monstro e Lógica de Turnos.
- **Sistema de Loot:** Drop de itens baseado em taxas reais do rAthena (RNG 1-10000) persistido no inventário.
- **Sistema de Nível:** Gestão de Experiência (Base/Job), Level Up automático e pontos de atributos.
- **Distribuição de Stats:** Menu terminal para gastar `statPoints` nos atributos (STR/AGI/VIT/INT/DEX/LUK).
- **Sistema de Skills:** Catálogo de skills por classe (`skill_tree`), prerequisites AND-logic, aprendizado com `skillPoints`, menu terminal acessível via `S` no status.
- **Sistema de Troca de Classe:** Progressão NOVICE→Tier1→Tier2 com validação de job level, menu via `C` no status.
- **Startup Automático:** `StartupDataLoader` popula todas as tabelas estáticas no boot — sem scripts manuais.
- **Testes:** Testes de integração cobrindo o ciclo "Atacar -> Matar -> Dropar -> Upar", skills e troca de classe.

---

## 🏗 Arquitetura e Organização

O projeto segue estritamente a separação de responsabilidades definida pela arquitetura hexagonal:

### 1. Domain (`com.ragnarok.domain`)
* **O que é:** O coração do sistema. Contém a lógica de negócio e os modelos puros.
* **Regra de Ouro:** Não deve ter dependências de Frameworks (Spring, Hibernate, Feign). Não usa anotações como `@Entity` ou `@Table`.
* **Tipagem:** Usa tipos fortes (`Integer`, `Double`) para cálculos matemáticos.

### 2. Application (`com.ragnarok.application`)
* **O que é:** A camada de orquestração (Use Cases).
* **Função:** Recebe comandos, busca dados usando as portas (Interfaces) e coordena o fluxo.
* **Exemplos:**
    * `MonsterCatalogService`: Coordena a busca na API e o salvamento no Banco.
    * `PlayerService`: Gerencia a criação de personagens e regras iniciais.
    * `BattleService`: Gerencia o ciclo de combate, morte e recompensas.
    * `SkillService`: Lista skills disponíveis por classe, valida pré-requisitos e processa o aprendizado.
    * `SkillRowDTO`: Record público com os dados de uma skill para exibição no terminal.

### 3. Infrastructure (`com.ragnarok.infrastructure`)
* **O que é:** Os adaptadores para o mundo externo.
* **Client:** Comunicação com APIs externas (Ragnapi). Usa DTOs "sujos" (Strings) para tolerar dados mal formatados.
* **Persistence:** Comunicação com Banco de Dados (PostgreSQL). Usa Entities com anotações JPA (`@Entity`).
* **Mapper:** A ponte tradutora que converte DTO (Infra) -> Domain -> Entity (Infra).

### 4. Runner (`com.ragnarok.runner`)
* `RagnarokTerminalRunner` — UI do terminal, game loop de exploração e combate.
* `RathenaImporter` — importa monstros e itens do rAthena automaticamente no startup (`@Order(1)`).
* `MockMapLoader` — cria o player inicial (`@Order(2)`). Não cria mais mocks de monstros ou mapas.
* `StartupDataLoader` — popula `maps`, `map_portals`, `map_monsters`, `monster_drops` e `skill_tree` a partir dos SQLs em `src/main/resources/db/` se as tabelas estiverem vazias (`@Order(3)`). **Sem scripts manuais necessários.**

### 5. "Battle System" (Ciclo de Batalha e Recompensa)
1. **Trigger:** `BattleService.realizarAtaque` é chamado.
2. **Engine (Domínio):** `BattleEngine` calcula o dano físico e `LevelingService` processa a XP.
3. **Aplicação:** Atualiza o HP do monstro e os dados do Player no banco.
4. **Loot & XP:** Se o monstro morrer, itens são dropados e a experiência é calculada, podendo acionar o Level Up.

---

## 🗺 Sistema de Navegação

O player navega pelo mundo usando dados reais de warps do rAthena.

- **`map_portals`** — 1864 warps extraídos de `npc/re/warps/`
- **`map_monsters`** — 2374 entradas de spawn extraídas de `npc/re/mobs/`
- **Localização do player** — salva em `PlayerEntity.mapName` (ex: `"prontera"`, `"prt_fild08"`)
- **Encontro aleatório** — monstro sorteado por peso proporcional ao `amount` do spawn

```
Exemplo prt_fild08: Poring x87, Fabre x77, Lunatic x67...
Poring aparece com probabilidade proporcional ao seu amount.
```

---

## 🗄 Banco de Dados

**URL:** `jdbc:postgresql://localhost:5432/ragnarok_db`
**User:** `postgres` / **Password:** *(configure via variável de ambiente `DB_PASS`, padrão local: `postgre`)*

| Tabela | Origem | Descrição |
|---|---|---|
| `monsters` | RathenaImporter (startup) | 2675 monstros do `db/re/mob_db.yml` |
| `items` | RathenaImporter (startup) | Itens do `db/re/item_db_*.yml` |
| `maps` | `maps.sql` | Todos os mapas do `db/map_index.txt` |
| `map_portals` | `map_portals_v2.sql` | 1864 warps de `npc/re/warps/` |
| `map_monsters` | `map_monsters.sql` | 2374 spawns de `npc/re/mobs/` |
| `monster_drops` | `monster_drops.sql` | 12544 drops com rate real do rAthena |
| `players` | MockMapLoader (startup) | Jogador inicial |
| `player_items` | Gerado em combate | Inventário do jogador |
| `skills` | SQL manual / seed | Catálogo de skills (aegis_name, name, type) |
| `skill_tree` | SQL manual / seed | Árvore de skills por classe (job_class, skill_id, max_level, prereqs) |
| `player_skills` | JPA ddl-auto | Skills aprendidas (player_id, skill_id, current_level) — auto-criada no startup |
| `monster_spawns` | MockMapLoader (startup) | Legado — substituído por `map_monsters` |

### Migrações automáticas

**Nenhum script manual necessário.** O `StartupDataLoader` (@Order 3) popula todas as tabelas automaticamente no startup se estiverem vazias. O console exibirá o progresso:

```
🔄 Populando map_monsters    a partir de db/map_monsters.sql...
✅ map_monsters    populada com 2374 registros.
```

> Os SQLs ficam em `src/main/resources/db/`. Os scripts Python em `scriptsPython/` são usados apenas para **regenerar** os SQLs quando os dados do rAthena mudam — após regen, copiar para `src/main/resources/db/`.

### Reset completo do banco (respeitar ordem de FK)

```sql
DELETE FROM monster_drops;
DELETE FROM map_monsters;
DELETE FROM monster_spawns;
DELETE FROM monsters;
DELETE FROM items;
```

---

## 🐍 Scripts Python (`scriptsPython/`)

Pipeline ETL que extrai dados do rAthena e gera SQLs para o banco.

| Script | Função |
|---|---|
| `Migrate.py` | Roda todos os SQLs no banco em ordem |
| `map_parser.py` | Gera `maps.sql` do `db/map_index.txt` |
| `warp_parser.py` | Gera `map_portals_v2.sql` de `npc/re/warps/` |
| `mob_parser.py` | Gera `map_monsters.sql` de `npc/re/mobs/` |
| `drop_parser.py` | Gera `monster_drops.sql` de `db/re/mob_db.yml` |

**Dependências Python:**
```bash
pip install requests pyyaml psycopg2-binary --break-system-packages
```

---

## 📂 Estrutura de Pastas Atual

```text
com.ragnarok
├── application
│   └── service
│       ├── MonsterCatalogService.java
│       ├── PlayerService.java
│       ├── ItemService.java            # Gestão de Itens e Auto-Swap de equipamentos
│       ├── BattleService.java
│       ├── SkillService.java           # listarSkillsDoPlayer + aprenderSkill
│       ├── SkillRowDTO.java            # Record público para exibição de skill no terminal
│       └── ClassChangeService.java     # listarClassesDisponiveis + trocarClasse
│
├── domain
│   └── model
│   │   ├── Monster.java
│   │   ├── Player.java
│   │   ├── Item.java
│   │   ├── ItemStats.java              # Value Object
│   │   ├── ItemType.java
│   │   ├── EquipSlot.java
│   │   ├── JobClass.java
│   │   ├── BattleResult.java
│   │   ├── ElementalDamage.java
│   │   ├── MainAttributes.java
│   │   ├── MainStats.java
│   │   ├── MonsterDrop.java
│   │   ├── PlayerItem.java
│   │   ├── PlayerLocation.java
│   │   ├── PlayerStats.java
│   │   └── ItemDropInfo.java
│   └── service
│       ├── BattleEngine.java
│       └── LevelingService.java        # Matemática de XP e Nível
│
└── infrastructure
    ├── client
    │   ├── RagnapiClient.java
    │   ├── dto
    │   │   ├── MonsterDTO.java
    │   │   └── ItemDTO.java
    │   └── mapper
    │       ├── MonsterMapper.java
    │       ├── ItemMapper.java
    │       └── PlayerMapper.java       # Safe Unboxing (NULL -> 0)
    │
    └── persistence
        ├── MonsterEntity.java
        ├── MonsterRepository.java
        ├── MonsterSpawnEntity.java     # Legado — substituído por MapMonsterEntity
        ├── MonsterSpawnRepository.java # Legado
        ├── MonsterDropEntity.java
        ├── GameMapEntity.java
        ├── GameMapRepository.java
        ├── MapPortalEntity.java
        ├── MapPortalRepository.java
        ├── MapMonsterEntity.java       # Spawns por mapa com amount (peso)
        ├── MapMonsterRepository.java
        ├── PlayerEntity.java
        ├── PlayerRepository.java
        ├── PlayerItemEntity.java       # UUID PK, flag is_equipped
        ├── PlayerItemRepository.java
        ├── ItemEntity.java
        ├── ItemRepository.java
        ├── SkillEntity.java            # Tabela 'skills'
        ├── SkillRepository.java        # findByAegisName()
        ├── SkillTreeEntity.java        # Tabela 'skill_tree' (read-only)
        ├── SkillTreeRepository.java    # findByJobClassIgnoreCase()
        ├── PlayerSkillEntity.java      # Tabela 'player_skills' (UUID PK)
        ├── PlayerSkillRepository.java  # findByPlayerId(), findByPlayerIdAndSkillId()
        └── mapper
            └── PlayerMapper.java
```

---

## 🔄 Fluxos de Dados Implementados (Data Flows)

### 1. "Monster Catalog" (ETL & Data Mining)
Fluxo resiliente que carrega dados externos, sanitiza inconsistências e realiza mineração de dados relacionais.

1. **Trigger:** `MonsterCatalogService.carregarESalvarMonstro(Long id)`.
2. **Busca (Client):** `RagnapiClient` consome a API externa.
    * *Input:* JSON "sujo" (ex: `"hp": "10,000"`, `"name": "scorpion"`).
3. **Sanitização (Mapper):** `MonsterMapper.toDomain(dto)`.
    * Remove formatação numérica (vírgulas).
    * Normaliza Strings (Caixa alta/baixa).
    * Garante Tipagem Forte (`String` -> `Integer`).
4. **Domínio:** Instanciação do objeto `Monster` puro para cálculos seguros.
5. **Persistência (Mapper):** `MonsterMapper.toEntity(domain)` aplica estratégia de **Flattening** (achata objetos aninhados em colunas planas).
6. **Mineração Automática (Data Mining):**
    * **Drops:** Identifica itens na lista de loot; se não existirem no banco, cria "Itens Placeholder" automaticamente.
    * **Spawns:** Analisa a URL do mapa (ex: `.../moc_fild08.png`), extrai o ID técnico (`moc_fild08`) e popula tabelas de `maps` e `monster_spawns`.

### 2. "Character Creation" (Factory & Persistence)
1. **Trigger:** `PlayerService.criarNovoPersonagem(nome, classe)`.
2. **Domínio:** Aplicação de regras de negócio (Nível 1, HP 100/100, Localização: Prontera).
3. **Mapeamento:** `PlayerMapper` converte objetos de valor (`PlayerStats`, `PlayerLocation`) para colunas primitivas na tabela `players`.
4. **Persistência:** Commit transacional no PostgreSQL.

### 3. "Item Management" (Inventory & Equipment)
1. **Gestão de Inventário (UUID):** Implementação robusta onde cada item possui um UUID único (`player_items`), permitindo múltiplas instâncias do mesmo item (ex: duas Katanas com refinos diferentes).
2. **Lógica de Auto-Swap:** Ao equipar um item, o `ItemService`:
    * Identifica o `EquipSlot` do novo item.
    * Verifica se há item já equipado neste slot.
    * Remove o antigo e equipa o novo em uma única transação atômica.
3. **Persistência de Loot:**
    * Tabela associativa `monster_drops` armazena a taxa de drop (`rate` 1-10000, onde 10000 = 100%).
    * Arquitetura suporta *Eager Loading* para disponibilizar a tabela de loot imediatamente ao iniciar o combate.

### 4. "Battle & Progression" (Engine & Leveling)
Fluxo completo de combate, recompensa e evolução de personagem.

1. **Trigger:** `BattleService.realizarAtaque(playerId, monsterId)`.
2. **Combate (Engine):** `BattleEngine` calcula dano físico (`(STR*2 + WeaponATK) - EnemyDEF`) e define o vencedor do turno.
3. **Counter-ataque:** Monstro responde no mesmo turno; dano e HP atual do jogador exibidos no terminal.
4. **Loot (RNG):** Se o monstro morre, a engine rola os dados de drop baseada nas taxas reais do rAthena. Itens ganhos são salvos na tabela `player_items`.
5. **Processamento de XP (LevelingService):**
    * **Extração:** Captura `baseExp` e `jobExp` do monstro.
    * **Cálculo:** `LevelingService` verifica a curva de experiência (`Level * 100`).
    * **Level Up:** Se a XP exceder o necessário:
        * Incrementa Nível Base/Job.
        * Adiciona **Pontos de Atributo** (+5) e **Skill Points** (+1).
        * Executa **Full Heal** (Recupera HP/SP máximos).
    * **Persistência:** Atualiza a entidade `PlayerEntity` com os novos saldos.

### 5. "World Navigation" (Map & Portals)
1. **Trigger:** Jogador seleciona "Portais" no menu de exploração.
2. **Consulta:** `MapPortalRepository.findDestinosByMapFrom(mapaAtual)` retorna destinos disponíveis.
3. **Viagem:** `PlayerEntity.mapName` é atualizado e persistido no banco.
4. **Encontro:** `MapMonsterRepository.findByMapId(mapaAtual)` retorna spawns do mapa. Sorteio ponderado pelo `amount`.
5. **Morte:** Player revive em `prontera`, `mapName` resetado.

### 6. "Skills System" (Aprendizado e Pré-requisitos)
1. **Trigger:** Jogador abre o menu de Status → digita `S`.
2. **Listagem:** `SkillService.listarSkillsDoPlayer(playerId)` busca a `skill_tree` da classe do player e resolve pré-requisitos com AND-logic. Retorna lista de `SkillRowDTO`.
3. **Exibição:** Terminal mostra status `[DISPONIVEL]`, `[BLOQUEADA: motivo]`, `[APRENDIDA]` ou `[MAX]` para cada skill.
4. **Aprendizado:** Jogador digita o número da skill → `SkillService.aprenderSkill(playerId, aegisName)` valida classe, pré-requisitos, nível máximo e `skillPoints`. Se válido, faz upsert em `player_skills` e decrementa `skillPoints` do player.
5. **Erros:** Violações lançam `IllegalStateException` capturada pelo terminal e exibida ao jogador.

### 7. "Safety & Resilience" (Null Safety)
Camada de proteção contra inconsistências de dados legados ou falhas de banco.

1. **Mapper Blindado:** `PlayerMapper` implementa *Safe Unboxing*.
    * Se o banco retornar `NULL` para campos numéricos (XP, Pontos, Zenny), o Mapper injeta `0` no Domínio.
    * Previne `NullPointerException` em tempo de execução durante cálculos matemáticos.
2. **Sanitização no Boot:** O `MockMapLoader` verifica e corrige registros corrompidos (campos nulos) ao inicializar a aplicação.

---

## 📝 Dicionário de Classes Chaves

### Domain Models

* **Monster:** Modelo rico do domínio.
    * *Evolução:* Agora expõe `baseExp` e `jobExp` para o cálculo de recompensas.
    * Possui atributos aninhados (`MainStats`) e tipos fortes.
* **Player:** Modelo rico do usuário.
    * *Evolução:* Contém lógica de inventário e gestão de progresso (`statPoints`, `skillPoints`, `xp`), além de `mapName` para localização no mundo.
* **Item:** Modelo puro representando itens e equipamentos. Possui `ItemStats` (Value Object).
* **MonsterDrop:** Objeto de domínio que associa um `Item` a uma chance de drop (`Double rate`).

### Domain Services (Regras de Negócio Puras)

* **BattleEngine:** Realiza cálculos matemáticos de dano físico e sorteio de loot (RNG). Não depende de banco de dados.
* **LevelingService:** Responsável pela matemática de progressão.
    * Define a curva de experiência (`Nível * 100`).
    * Processa o Level Up (Base e Job).
    * Gerencia recompensas (Cura total, pontos de atributo e skill).

### Infrastructure DTOs

* **MonsterDTO:** Registro (Record) imutável desenhado para falhar o menos possível. Quase todos os campos são `String` para tolerar dados sujos da API.

### Infrastructure Entities (Persistence)

* **MonsterEntity:** Entidade JPA mapeada na tabela `monsters`.
    * *Estratégia:* **Flattening**. "Achata" objetos complexos em colunas simples (ex: `stats.hp` vira `hp`).
* **PlayerEntity:** Entidade JPA mapeada na tabela `players`.
    * *Colunas:* `base_exp`, `job_exp`, `stat_points`, `skill_points`, `map_name`.
    * Também utiliza Flattening para atributos de batalha e localização.
* **GameMapEntity:** Tabela `maps`.
    * *Diferencial:* O ID é o nome técnico do mapa (ex: `moc_fild08`) para facilitar a leitura e unicidade.
* **MapPortalEntity:** Tabela `map_portals`.
    * *Função:* Armazena os 1864 warps reais do rAthena com coordenadas de origem e destino.
* **MapMonsterEntity:** Tabela `map_monsters`.
    * *Função:* Associa monstros a mapas com `amount` (peso de spawn). Substitui `MonsterSpawnEntity` para dados reais do rAthena.
* **MonsterSpawnEntity:** Tabela `monster_spawns`. **Legado** — substituído por `MapMonsterEntity`.
    * *Função original:* Resolve o relacionamento Muitos-para-Muitos (Monstros <-> Mapas), controlando quantidade e respawn.
* **MonsterDropEntity:** Tabela `monster_drops`. Tabela associativa que liga Monstros e Itens com taxa de chance real do rAthena (1-10000).
* **PlayerItemEntity:** Tabela `player_items`. Representa o inventário, contendo UUID próprio, referência ao Item, refino e flag `is_equipped`.
* **SkillEntity:** Tabela `skills`. Catálogo de skills com `aegisName`, `name` e `type`.
* **SkillTreeEntity:** Tabela `skill_tree`. Read-only. Cada linha representa uma skill disponível para uma classe, com `maxLevel`, `prereqSkill` e `prereqLevel`. Uma skill pode ter múltiplas linhas (um pré-requisito por linha).
* **PlayerSkillEntity:** Tabela `player_skills`. Criada automaticamente via JPA. Armazena o nível atual de cada skill aprendida pelo player (UUID PK, unique constraint em `player_id + skill_id`).

### Mappers (Camada de Tradução e Segurança)

* **MonsterMapper:** Crítico para resiliência. Contém lógica de `try-catch` e sanitização de dados externos.
* **PlayerMapper:** **[Blindado]** Focado em estruturar objetos complexos em tabela plana.
    * *Feature:* Implementa **Safe Unboxing**. Se o banco retornar `NULL` para campos numéricos críticos (XP, Pontos), o Mapper converte automaticamente para `0` antes de instanciar o Domínio, prevenindo `NullPointerException` na lógica de negócio.

---

## ✅ Cobertura de Testes (Integration Status)

O projeto mantém uma bateria de testes de integração focados nos fluxos críticos do RPG:

### 🟢 Core & ETL
* **MonsterCatalogServiceTest:** Valida o fluxo completo API Externa -> Sanitização -> Persistência -> Mineração de Spawns/Drops.
* **PlayerServiceTest:** Valida a Factory de personagens, garantindo status iniciais e persistência correta (Flattening).
* **ItemServiceTest:** Valida a persistência de itens e a integridade dos dados "achatados" no banco.

### 🟢 Gameplay & Mechanics
* **BattleIntegrationTest:** Valida a matemática de dano físico (`(STR*2 + Weapon) - DEF`).
* **BattleLootIntegrationTest:** Valida a tabela de drop (RNG) e o salvamento do item na mochila.
* **LevelingIntegrationTest:** Valida a curva de experiência, o reset de XP excedente e a entrega de recompensas (Pontos e Full Heal).
* **PlayerInventoryTest:** Valida a lógica de `Equip`/`Unequip` e a troca automática de slots (Auto-Swap).
* **SkillServiceIntegrationTest:** Valida `listarSkillsDoPlayer` — lista não vazia para Novice, marcação de skills disponíveis com skillPoints > 0.
* **SkillServiceAprenderTest:** Valida `aprenderSkill` — incremento de nível, decremento de skillPoints, `IllegalStateException` sem pontos ou em nível máximo.
* **ClassChangeIntegrationTest:** Valida troca de classe no banco real — `trocarClasse` persiste `jobClass/jobLevel/jobExp`, `listarClassesDisponiveis` retorna tier-1 para NOVICE.

### 🟢 Unit Tests (Mockito)
* **BattleEngineTest:** 17 cenários cobrindo fórmula de dano, dano mínimo 1, DEF=0, loot 100%/0%, counter-attack e null guards.
* **BattleServiceTest:** 8 cenários — ataque normal, morte do monstro (VITÓRIA), contra-ataque, morte do jogador (FATAL), player/monster not found, player já morto.
* **ClassChangeServiceTest:** Cenários de `listarClassesDisponiveis` e `trocarClasse` com mocks de repositórios.
* **RagnarokTerminalRunnerTest:** Valida ressurreição automática do jogador ao receber `FATAL` do BattleService.

---

## 🚀 Roadmap & Backlog

### ✅ Concluído
* **Menu de Distribuição de Stats:** Terminal permite gastar `statPoints` nos 6 atributos base.
* **Sistema de Skills:** `skill_tree`/`player_skills`, SkillService com listagem e aprendizado, menu terminal via `S`.
* **Sistema de Troca de Classe:** `ClassChangeService`, progressão NOVICE→tier1→tier2, menu terminal via `C` no status.
* **Startup automático:** `StartupDataLoader` popula todas as tabelas na inicialização — zero scripts manuais.
* **LevelingService:** Caps de nível base/job por classe.
* **Limpeza de tela no terminal:** `clearScreen()` com ANSI codes entre cada menu — sem scroll infinito.
* **Inventário empilha corretamente:** Drop de item existente incrementa `amount` no mesmo slot.
* **Stat/skill points acumulam em multi-levelup:** `PlayerMapper.toDomain` agora mapeia `statPoints` e `skillPoints` do banco antes de aplicar os ganhos do level up.

---

### 🐛 Bugs Conhecidos

Nenhum bug conhecido no momento.

---

### 🎮 Funcionalidades Faltando (Backlog)

#### Alta Prioridade
1. **Usar itens em batalha** — menu de combate só tem "Atacar" e "Fugir". Adicionar opção "Item" para usar consumíveis do inventário durante o combate.
2. **Usar skills em batalha** — integrar skills aprendidas na `BattleEngine`. Dano mágico (INT), habilidades especiais com custo de SP.
3. **Itens de retorno (Butterfly Wing / Fly Wing)** — implementar mecânica de teleporte: Butterfly Wing → Prontera, Fly Wing → ponto aleatório do mapa atual.
4. **Mecânicas dos stats faltantes:**
   - **AGI** → FLEE (evasão de ataques) e ASPD (velocidade de ataque)
   - **DEX** → HIT (precisão) e redução de cast time de magias
   - **LUK** → taxa de crítico e bônus de drop rate

#### Média Prioridade
5. **Enciclopédia de mapas** — menu informativo mostrando: mapas vizinhos acessíveis, monstros do mapa atual com taxa de spawn, drops de cada monstro com raridade.
6. **Sistema de lojas NPC** — compra e venda de itens com Zenny em cidades específicas (Prontera, Morroc, etc.).
7. **Troca de classe restrita a NPCs** — atualmente disponível em qualquer lugar. Deveria ser permitida apenas em cidades/NPCs específicos (ex: Prontera para tier 1, locais específicos para tier 2).

#### Experiência / UX
8. **Dano mágico testável** — INT não tem mecânica visível ainda. Implementar e documentar a fórmula.

#### Futuro
* **Persistência de posição (X,Y)** — salvar coordenadas ao sair, não apenas o mapa.
* **Conexões de borda entre mapas** — campos conectados por borda geográfica (sem NPC warp) ausentes do `map_portals`.
* **Multiplayer (WebSockets)** — (Longo Prazo).

---

## ⚙️ Build & Run

```bash
# Build
./mvnw clean install

# Rodar aplicação
./mvnw spring-boot:run

# Rodar todos os testes
./mvnw test

# Rodar teste específico
./mvnw test -Dtest=BattleIntegrationTest
```
