# Ragnarok Core - Hexagonal Architecture Refactoring

Este projeto é o núcleo (Core) de um sistema de emulação e gerenciamento de dados baseado no jogo Ragnarok Online. O projeto está passando por uma refatoração profunda para **Arquitetura Hexagonal (Ports and Adapters)**, visando desacoplar as regras de negócio (Domínio) de frameworks externos, banco de dados e APIs. Toda a base de dados do jogo (monstros, itens, mapas, warps, drops) é importada diretamente do servidor oficial **rAthena** (`db/re/` — versão Renewal).

---

## ✅ Status do Projeto (Fase 1 & 2 em Andamento)
- **Arquitetura:** Hexagonal (Domain, Application, Infrastructure).
- **Dados do jogo:** Importados do rAthena `db/re/` — 2675 monstros, todos os itens, 1864 warps, 2374 spawns, 12544 drops.
- **Navegação:** Player se move entre mapas reais usando portais do rAthena.
- **Engine de Batalha:** Cálculo de dano físico (Status + Equipamentos + Defesa), counter-ataque do monstro e Lógica de Turnos.
- **Sistema de Loot:** Drop de itens baseado em taxas reais do rAthena (RNG 1-10000) persistido no inventário.
- **Sistema de Nível:** Gestão de Experiência (Base/Job), Level Up automático e pontos de atributos.
- **ETL Pipeline:** Scripts Python em `scriptsPython/` para importar dados do rAthena para o banco.
- **Testes:** Testes de integração cobrindo o ciclo "Atacar -> Matar -> Dropar -> Upar".

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

### 3. Infrastructure (`com.ragnarok.infrastructure`)
* **O que é:** Os adaptadores para o mundo externo.
* **Client:** Comunicação com APIs externas (Ragnapi). Usa DTOs "sujos" (Strings) para tolerar dados mal formatados.
* **Persistence:** Comunicação com Banco de Dados (PostgreSQL). Usa Entities com anotações JPA (`@Entity`).
* **Mapper:** A ponte tradutora que converte DTO (Infra) -> Domain -> Entity (Infra).

### 4. Runner (`com.ragnarok.runner`)
* `RagnarokTerminalRunner` — UI do terminal, game loop de exploração e combate.
* `RathenaImporter` — importa monstros e itens do rAthena automaticamente no startup (`@Order(1)`).
* `MockMapLoader` — cria o player inicial e popula dados básicos (`@Order(2)`).

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
**User:** `postgres` / **Password:** `postgre`

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
| `monster_spawns` | MockMapLoader (startup) | Legado — substituído por `map_monsters` |

### Rodando migrações

```bash
cd scriptsPython
pip install psycopg2-binary --break-system-packages
python3 Migrate.py
```

> ⚠️ Sempre usar `map_portals_v2.sql` — nunca o `map_portals.sql` original (incompleto).

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
│       ├── ItemService.java            # Gestão de Itens
│       └── BattleService.java
│
├── domain
│   └── model
│   │   ├── Monster.java
│   │   ├── Player.java
│   │   ├── Item.java                   # Modelo Puro de Item
│   │   ├── ItemStats.java              # Value Object
│   │   ├── MonsterDrop.java
│   │   └── ...
│   └── service
│       ├── BattleEngine.java
│       └── LevelingService.java        # Matemática de XP e Nível
│
└── infrastructure
    ├── client
    │   ├── RagnapiClient.java
    │   ├── dto
    │   │   ├── MonsterDTO.java
    │   │   └── ItemDTO.java            # DTO para API externa
    │   └── mapper
    │       ├── MonsterMapper.java      # Conversor de API (DTO -> Domain)
    │       ├── ItemMapper.java         # Conversor Híbrido (DTO <-> Domain <-> Entity)
    │       └── PlayerMapper.java
    │
    └── persistence
        ├── MonsterEntity.java
        ├── MonsterRepository.java
        ├── MonsterSpawnEntity.java     # Legado — substituído por MapMonsterEntity
        ├── MonsterDropEntity.java
        ├── GameMapEntity.java          # Tabela de Mapas (moc_fild08)
        ├── GameMapRepository.java
        ├── MapPortalEntity.java        # Portais reais do rAthena
        ├── MapPortalRepository.java
        ├── MapMonsterEntity.java       # Spawns por mapa com amount (peso)
        ├── MapMonsterRepository.java
        ├── PlayerEntity.java
        ├── PlayerRepository.java
        ├── PlayerItemEntity.java
        ├── PlayerMapper.java           # Mapper de Banco junto com Persistência
        ├── ItemEntity.java             # Tabela 'items'
        └── ItemRepository.java
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

### 6. "Safety & Resilience" (Null Safety)
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

---

## 🚀 Roadmap & Backlog

O foco atual é fechar o ciclo de progressão do jogador e aumentar a complexidade do combate.

### Prioridade Alta (Próxima Sprint)
1. **Menu de Distribuição (UI):** Criar interface no Terminal para o jogador gastar os `statPoints` acumulados.
2. **Sistema de Habilidades (Skills):**
    * Criar entidade `Skill` e `PlayerSkill`.
    * Implementar menu para gastar `skillPoints`.
    * Integrar skills na `BattleEngine` (Dano Mágico/Físico Especial).
3. **Refatoração Elementar:** Atualizar a Engine para considerar os elementos (Fogo x Água) e tamanhos (Pequeno/Médio/Grande) no cálculo de dano.

### Futuro
* **Persistência de Estado do Mapa:** Salvar a posição (X,Y) do jogador ao sair.
* **Sistema de Lojas (NPCs):** Compra e venda de itens com Zenny.
* **Conexões de borda entre mapas:** Campos conectados por borda (sem NPC warp) ainda não estão no `map_portals`.
* **Multiplayer (WebSockets):** (Longo Prazo) Permitir interação entre jogadores.

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
