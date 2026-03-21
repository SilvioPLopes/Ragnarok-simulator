# Design: Script Pipeline — Data + Interpreter + Integration

**Data:** 2026-03-17
**Status:** Aprovado (rev 3 — pós spec-review iteração 2)

## Contexto

O Ragnarok-simulator tem UI de batalha completa (skills, itens, fuga), serviços de aplicação conectados ao `ScriptInterpreter`, e entidades com campo `script` definido. O problema é puramente de dados e interpretador incompleto:

- Tabela `skills` nunca é carregada no startup → `usarSkillEmCombate` sempre falha
- `skills.script = NULL` em todas as linhas do SQL gerado → skills não executam
- `items.script = NULL` no banco (importação anterior ao parser ter o campo) → itens caem no fallback legado
- `ScriptInterpreter` só entende `itemheal` → maioria dos comandos retorna `unsupported()`

## Objetivo

Ciclo completo em 3 agentes paralelos:
**dados corretos → interpretador funcional → integrado em batalha**

---

## Pré-requisito de implantação (executar antes do próximo startup)

```sql
DELETE FROM items;
DELETE FROM skills;
```

Isso força reimportação completa de `items` (com `script` populado) e permite que o `skills.sql` regenerado seja aplicado. Os loaders são idempotentes — se as tabelas estiverem populadas, pulam.

> ⚠️ Sem o `DELETE FROM items`, `ItemService` cai no path legado (`item.getScript() == null`) e o novo código do interpretador nunca é atingido durante testes.

---

## Agente 1 — Data Pipeline

### 1.1 StartupDataLoader.java

**Problema:** `loadIfEmpty` usa `COUNT(*) > 0` — se `skills` já tem linhas com `script = NULL`, o loader pula o arquivo regenerado silenciosamente.

**Solução:** Adicionar método `forceLoad` que executa o SQL diretamente aproveitando o `ON CONFLICT DO UPDATE` já presente no `skills.sql`. A tabela `skills` usa `forceLoad`; todas as outras continuam com `loadIfEmpty`.

**Método a adicionar:**

```java
private void forceLoad(String sqlFile) {
    try (Connection conn = dataSource.getConnection()) {
        ScriptUtils.executeSqlScript(conn, new ClassPathResource(sqlFile));
        System.out.printf("✅ %-20s recarregada (force update).%n", sqlFile);
    } catch (Exception e) {
        System.err.println("ERRO ao carregar " + sqlFile + ": " + e.getMessage());
    }
}
```

**Corpo do `run()` atualizado — mostrado completo para evitar ambiguidade:**

```java
@Override
public void run(String... args) throws Exception {
    ensureUniqueConstraints();

    forceLoad("db/skills.sql");          // sempre atualiza scripts via ON CONFLICT
    loadIfEmpty("maps",          "db/maps.sql",            0);
    loadIfEmpty("map_portals",   "db/map_portals_v2.sql",  0);
    loadIfEmpty("map_monsters",  "db/map_monsters.sql",    0);
    loadIfEmpty("monster_drops", "db/monster_drops.sql",   0);
    loadIfEmpty("skill_tree",    "db/skill_tree.sql",      0);
}
```

> **Nota:** `skills.sql` usa `CREATE TABLE IF NOT EXISTS` + `INSERT ... ON CONFLICT (id) DO UPDATE SET name, type, script`. É seguro e idempotente rodar a cada startup.

### 1.2 skill_parser.py — Investigação e Correção

**Tarefa:**
1. Baixar `https://raw.githubusercontent.com/rathena/rathena/master/db/re/skill_db.yml`
2. Inspecionar a estrutura real do campo `Script` nas entries — mapear quais skills têm script e qual o formato exato
3. Corrigir extração em `skill_parser.py` se necessário
4. Regenerar `skills.sql`
5. Copiar para **`src/main/resources/db/skills.sql`** (caminho exato)

**Formatos esperados no rAthena:**
- Itens: `itemheal 100, 0;` — separado por espaço
- Skills: `heal(skill_lv * 100, 0)` — com parênteses

**Se nenhuma skill tiver `Script` no YAML:** registrar no commit e deixar tudo `NULL` — `ScriptInterpreter` retorna `unsupported()` de forma segura. Não é falha.

### 1.3 items.script — Reimportação

Sem alteração de código. `ItemDbParser` já extrai `item.get("Script")`. Usuário executa `DELETE FROM items` (ver pré-requisito). `RathenaImporter` reimporta no próximo startup.

### 1.4 README

Adicionar seção "Resetando dados do banco" com os comandos de `DELETE` e explicação do fluxo.

### Arquivos alterados
- `src/main/java/com/ragnarok/runner/StartupDataLoader.java`
- `scriptsPython/skill_parser.py`
- `scriptsPython/skills.sql`
- `src/main/resources/db/skills.sql`
- `README.md`

---

## Agente 2 — ScriptInterpreter

> **Pré-requisito de teste:** executar `DELETE FROM items` + restart antes de testar itens com script.

### 2.1 Decisão arquitetural — ScriptInterpreter como Spring Bean

**Regra do projeto:** `domain` package nunca depende de Spring (CLAUDE.md).

**Resolução:** Mover `ScriptInterpreter` para `application.service` e adicionar `@Component`.

**Operações de arquivo obrigatórias:**
1. Criar `src/main/java/com/ragnarok/application/service/ScriptInterpreter.java` com `package com.ragnarok.application.service` e `@Component`
2. Deletar `src/main/java/com/ragnarok/domain/service/ScriptInterpreter.java`

**Injeção via construtor — substituir field initializer:**

Em `ItemService.java` — remover linha 25 (`= new ScriptInterpreter()`) e adicionar ao construtor:
```java
// ANTES (linha 25):
private final ScriptInterpreter scriptInterpreter = new ScriptInterpreter();

// DEPOIS — campo sem inicialização + parâmetro no construtor:
private final ScriptInterpreter scriptInterpreter;

public ItemService(..., ScriptInterpreter scriptInterpreter) {
    ...
    this.scriptInterpreter = scriptInterpreter;
}
```

Mesmo padrão em `SkillService.java` linha 21. Atualizar ambos os imports para `com.ragnarok.application.service.ScriptInterpreter`.

### 2.2 EffectResult.java

Adicionar `isPercent` como 5º componente do record. **Todos os 4 factory methods existentes devem ser atualizados para incluir o novo argumento `false`** — são eles: `heal`, `unsupported`, e os dois novos (`percentHeal`, `damage`). O único call site que usa o construtor canônico diretamente é `ScriptInterpreter.java` — Agent 2 atualiza ambos atomicamente.

**Contrato dos factories:**
- `EffectResult.heal(int hp, int sp)` — valores absolutos, `isPercent = false` (sem mudança de semântica)
- `EffectResult.percentHeal(int hpPct, int spPct)` — valores 0–100, `isPercent = true` (novo)
- `EffectResult.damage(int value)` — dano, `isPercent = false` (novo)
- `EffectResult.unsupported()` — `isPercent = false` (sem mudança)

```java
public record EffectResult(
        int hpHeal,
        int spHeal,
        int damage,
        boolean supported,
        boolean isPercent
) {
    public static EffectResult heal(int hp, int sp) {
        return new EffectResult(hp, sp, 0, true, false);
    }
    public static EffectResult percentHeal(int hpPct, int spPct) {
        return new EffectResult(hpPct, spPct, 0, true, true);
    }
    public static EffectResult damage(int value) {
        return new EffectResult(0, 0, value, true, false);
    }
    public static EffectResult unsupported() {
        return new EffectResult(0, 0, 0, false, false);
    }
}
```

### 2.3 ScriptInterpreter.java

**Assinatura com backward-compatibility** (resolve `skill_lv`):

```java
@Component
public class ScriptInterpreter {

    public EffectResult interpret(String script) {
        return interpret(script, 1);
    }

    public EffectResult interpret(String script, int skillLevel) {
        if (script == null || script.isBlank()) return EffectResult.unsupported();

        // Substitui skill_lv pelo nível real antes de parsear
        String resolved = script.replace("skill_lv", String.valueOf(skillLevel));
        String trimmed = resolved.trim();

        // Extrai comando principal (palavra antes de espaço ou parêntese)
        int end = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '(') { end = i; break; }
        }
        String command = trimmed.substring(0, end).toLowerCase();

        return switch (command) {
            case "itemheal"    -> parseItemheal(trimmed);
            case "heal"        -> parseHeal(trimmed);
            case "percentheal" -> parsePercentheal(trimmed);
            case "sc_start"    -> EffectResult.unsupported(); // placeholder
            default            -> EffectResult.unsupported();
        };
    }
}
```

**Novos comandos:**

| Comando | Formatos suportados | Retorno |
|---------|---------------------|---------|
| `heal` | `heal arg, arg;` ou `heal(arg, arg)` | `EffectResult.heal(hp, sp)` |
| `percentheal` | `percentheal arg, arg;` | `EffectResult.percentHeal(hpPct, spPct)` |
| `sc_start` | qualquer | `unsupported()` |

**Regra de ouro:** nenhum comando lança exceção — sempre retorna `unsupported()` em caso de parse error.

### 2.4 ItemService.java

Injetar `ScriptInterpreter` via construtor (remover `new`). Atualizar `consumirItem` para tratar `isPercent`:

```java
EffectResult result = scriptInterpreter.interpret(item.getScript());
if (!result.supported()) return "Este item não pode ser usado ainda.";

int hpHeal = result.isPercent()
        ? hpMax * result.hpHeal() / 100
        : result.hpHeal();
int spHeal = result.isPercent()
        ? spMax * result.spHeal() / 100
        : result.spHeal();

return aplicarCura(player, itemEntity, hpHeal, spHeal, nome);
```

### 2.5 Testes

**`ScriptInterpreterTest.java`** (novo):
- `itemheal 100, 0;` → `heal(100, 0)`
- `heal 50, 20;` → `heal(50, 20)`
- `heal(50, 20)` → `heal(50, 20)`
- `percentheal 10, 5;` → `percentHeal(10, 5)`, `isPercent = true`
- `itemheal rand(50,100), 0;` → `hpHeal` entre 50–100
- `heal(skill_lv * 100, 0)` com `skillLevel=3` → `heal(300, 0)`
- `sc_start SC_INCREASEAGI, 60, 3, 0;` → `unsupported()`
- `null` → `unsupported()`
- `""` → `unsupported()`
- comando desconhecido → `unsupported()`

**`ItemServiceTest.java`** (atualizar): adicionar teste cobrindo o path `percentheal` em `consumirItem`.

### Arquivos alterados
- `src/main/java/com/ragnarok/application/service/ScriptInterpreter.java` *(movido de domain)*
- `src/main/java/com/ragnarok/domain/service/ScriptInterpreter.java` *(deletar)*
- `src/main/java/com/ragnarok/domain/model/EffectResult.java`
- `src/main/java/com/ragnarok/application/service/ItemService.java`
- `src/main/java/com/ragnarok/application/service/SkillService.java` *(só injeção do bean)*
- `src/test/java/com/ragnarok/domain/service/ScriptInterpreterTest.java` *(novo)*
- `src/test/java/com/ragnarok/application/service/ItemServiceTest.java` *(atualizar)*

---

## Agente 3 — Integration

### 3.1 SkillService.java — constructor + método completo

**Constructor atualizado** (adicionar `MonsterRepository` como 5º parâmetro):

```java
public SkillService(PlayerRepository playerRepository,
                    SkillTreeRepository skillTreeRepository,
                    PlayerSkillRepository playerSkillRepository,
                    SkillRepository skillRepository,
                    MonsterRepository monsterRepository) {
    this.playerRepository      = playerRepository;
    this.skillTreeRepository   = skillTreeRepository;
    this.playerSkillRepository = playerSkillRepository;
    this.skillRepository       = skillRepository;
    this.monsterRepository     = monsterRepository;
}
```

**`usarSkillEmCombate` completo:**

```java
@Transactional
public String usarSkillEmCombate(Long playerId, String aegisName, Long monsterId) {
    SkillEntity skill = skillRepository.findByAegisName(aegisName)
            .orElseThrow(() -> new IllegalStateException("Skill " + aegisName + " não encontrada."));

    PlayerSkillEntity playerSkill = playerSkillRepository
            .findByPlayerIdAndSkillId(playerId, aegisName)
            .filter(ps -> ps.getCurrentLevel() > 0)
            .orElseThrow(() -> new IllegalStateException("Você não aprendeu " + aegisName + " ainda."));

    PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
    int spAtual = player.getSpCurrent() != null ? player.getSpCurrent() : 0;

    if (spAtual < SP_CUSTO_SKILL) {
        throw new IllegalStateException("SP insuficiente. Necessário: " + SP_CUSTO_SKILL + ", atual: " + spAtual);
    }

    int skillLevel = playerSkill.getCurrentLevel();
    EffectResult result = scriptInterpreter.interpret(skill.getScript(), skillLevel);

    if (!result.supported()) {
        throw new IllegalStateException("Esta skill não pode ser usada ainda.");
    }

    String mensagem;

    if (result.damage() > 0) {
        MonsterEntity monster = monsterRepository.findById(monsterId)
                .orElseThrow(() -> new IllegalStateException("Monstro não encontrado."));
        int novoHp = Math.max(0, monster.getHp() - result.damage());
        monster.setHp(novoHp);
        monsterRepository.save(monster);
        mensagem = String.format("Você usou %s (Lv %d) e causou %d de dano no %s!",
                aegisName, skillLevel, result.damage(), monster.getName());
    } else if (result.hpHeal() > 0) {
        int hpAtual = player.getHpCurrent() != null ? player.getHpCurrent() : 0;
        int hpMax   = player.getHpMax()     != null ? player.getHpMax()     : 100;
        int novoHp  = Math.min(hpMax, hpAtual + result.hpHeal());
        int curado  = novoHp - hpAtual;
        player.setHpCurrent(novoHp);
        mensagem = String.format("Você usou %s (Lv %d) e recuperou %d de HP.", aegisName, skillLevel, curado);
    } else {
        mensagem = aegisName + " foi usada (sem efeito imediato).";
    }

    player.setSpCurrent(spAtual - SP_CUSTO_SKILL); // SP_CUSTO_SKILL já existe na linha 15
    playerRepository.save(player);

    return mensagem;
}
```

### Arquivos alterados
- `src/main/java/com/ragnarok/application/service/SkillService.java`

---

## Paralelização e Dependências

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   AGENTE 1      │  │   AGENTE 2      │  │   AGENTE 3      │
│   Data          │  │   Interpreter   │  │   Integration   │
│                 │  │                 │  │                 │
│ StartupData     │  │ ScriptInterp.   │  │ SkillService    │
│ skill_parser    │  │ EffectResult    │  │ + MonsterRepo   │
│ skills.sql x2   │  │ ItemService     │  │ (construtor     │
│ README          │  │ Tests           │  │  + método)      │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         └───────────────────┬┘────────────────────-┘
                    Commit de integração final
```

**Zero conflitos de arquivo** — nenhum arquivo tocado por mais de um agente.

**Dependência compilável:** Agente 3 usa `result.damage()` que já existe no record. O factory method `EffectResult.damage(int)` do Agente 2 é convenência — se necessário, Agente 3 pode usar `new EffectResult(0, 0, value, true, false)`.

**`ScriptInterpreter` movido:** Agente 2 deleta `domain/service/ScriptInterpreter.java` e cria `application/service/ScriptInterpreter.java`. Agente 3 usa `SkillService` que já importa do novo pacote (Agente 2 atualiza o import).

## Fora do Escopo

- Custo real de SP por skill/nível (fixo em `SP_CUSTO_SKILL = 10`)
- Skills de dano sem campo `Script` no rAthena (ficam como `unsupported()`)
- Status effects reais (`sc_start`)
- Reimportação automática de `items` via código
