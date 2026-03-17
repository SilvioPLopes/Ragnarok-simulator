# Design: Script Pipeline — Data + Interpreter + Integration

**Data:** 2026-03-17
**Status:** Aprovado

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

## Agente 1 — Data Pipeline

### 1.1 StartupDataLoader.java

Adicionar `loadIfEmpty("skills", "db/skills.sql", 0)` **antes** de `skill_tree`:

```java
loadIfEmpty("skills",        "db/skills.sql",           0);
loadIfEmpty("skill_tree",    "db/skill_tree.sql",        0);
```

Ordem obrigatória: `skills` antes de `skill_tree` (convenção de integridade lógica).

### 1.2 skill_parser.py — Investigação e Correção

**Hipótese:** skills de suporte/cura (`AL_HEAL`, `AL_INCAGI`) têm campo `Script` no `skill_db.yml`. Skills de dano direto (`MG_FIREBOLT`, `WZ_METEOR`) não têm — usam `Damage` flags e fórmulas hardcoded no servidor rAthena.

**Tarefa do agente:**
1. Baixar `https://raw.githubusercontent.com/rathena/rathena/master/db/re/skill_db.yml`
2. Inspecionar estrutura real do campo `Script` nas entries
3. Corrigir extração em `skill_parser.py` se necessário
4. Regenerar `skills.sql` com scripts corretos
5. Copiar para `src/main/resources/db/skills.sql`

**Comportamento esperado no SQL gerado:**
- Skills com script real: `script = 'heal(skill_lv * 100, 0)'` (exemplo)
- Skills sem script: `script = NULL` (correto, não é bug)

### 1.3 items.script — Reimportação

**Sem alteração de código.** O `ItemDbParser` já extrai `item.get("Script")` e o `RathenaImporter` já importa quando `count() == 0`.

**Procedimento:**
```sql
DELETE FROM items;
```
No próximo startup, `RathenaImporter` reimporta com `script` populado.

**README:** documentar esse procedimento na seção "Banco de Dados".

### Arquivos alterados
- `src/main/java/com/ragnarok/runner/StartupDataLoader.java`
- `scriptsPython/skill_parser.py`
- `scriptsPython/skills.sql`
- `src/main/resources/db/skills.sql`
- `README.md`

---

## Agente 2 — ScriptInterpreter

### 2.1 EffectResult.java

Adicionar factory method para dano (o campo `damage` já existe no record):

```java
public static EffectResult damage(int value) {
    return new EffectResult(0, 0, value, true);
}
```

### 2.2 ScriptInterpreter.java — Novos Comandos

| Comando | Formato | Comportamento |
|---------|---------|---------------|
| `heal` | `heal <hp>, <sp>;` | Alias de `itemheal` — cura valores fixos |
| `percentheal` | `percentheal <hp%>, <sp%>;` | Retorna `EffectResult.heal(hpPct, spPct)` com flag `isPercent=true` — caller aplica % |
| `sc_start` | `sc_start SC_XXX, ...;` | Retorna `unsupported()` — placeholder seguro, nunca lança exceção |

> **Nota sobre `percentheal`:** como `EffectResult` é um record simples sem contexto do jogador, o valor percentual é passado como inteiro (ex: `10` = 10%). O caller (`ItemService.consumirItem`) aplica a % sobre `hpMax`/`spMax`. Isso requer ajuste mínimo no caller — documentado aqui para o Agente 3 não ser necessário, já que `ItemService` é escopo do Agente 2.

**Atualização em `ItemService.consumirItem`:**
```java
if (result.isPercent()) {
    hpHeal = hpMax * result.hpHeal() / 100;
    spHeal = spMax * result.spHeal() / 100;
}
```
_(ou adicionar campo `isPercent` ao EffectResult — decisão do agente durante implementação)_

**Regra de ouro:** comando não reconhecido sempre retorna `unsupported()`, nunca lança exceção.

### 2.3 Testes Unitários — ScriptInterpreterTest.java

Cobrir:
- `itemheal 100, 0;` → `heal(100, 0)`
- `heal 50, 20;` → `heal(50, 20)`
- `percentheal 10, 5;` → percent heal 10% HP, 5% SP
- `itemheal rand(50,100), 0;` → heal entre 50-100
- `sc_start SC_INCREASEAGI, 60, 3, 0;` → `unsupported()`
- `null` → `unsupported()`
- `""` → `unsupported()`
- comando desconhecido → `unsupported()`

### Arquivos alterados
- `src/main/java/com/ragnarok/domain/model/EffectResult.java`
- `src/main/java/com/ragnarok/domain/service/ScriptInterpreter.java`
- `src/main/java/com/ragnarok/application/service/ItemService.java` (ajuste `percentheal`)
- `src/test/java/com/ragnarok/domain/service/ScriptInterpreterTest.java` (novo)

---

## Agente 3 — Integration

### 3.1 SkillService.java

**Problema atual:** `usarSkillEmCombate` recebe `monsterId` mas nunca usa. Trata apenas `hpHeal > 0`.

**Mudanças:**

1. Injetar `MonsterRepository` no construtor
2. Adicionar tratamento de `result.damage() > 0`:

```java
if (result.damage() > 0) {
    MonsterEntity monster = monsterRepository.findById(monsterId)
        .orElseThrow(() -> new IllegalStateException("Monstro não encontrado."));
    int novoHp = Math.max(0, monster.getHp() - result.damage());
    monster.setHp(novoHp);
    monsterRepository.save(monster);
    mensagem = String.format("Você usou %s e causou %d de dano no %s!",
        aegisName, result.damage(), monster.getName());
}
```

3. Sem alteração em `BattleService`, `RagnarokTerminalRunner` ou qualquer outro arquivo.

### Arquivos alterados
- `src/main/java/com/ragnarok/application/service/SkillService.java`

---

## Paralelização

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   AGENTE 1      │  │   AGENTE 2      │  │   AGENTE 3      │
│   Data          │  │   Interpreter   │  │   Integration   │
│                 │  │                 │  │                 │
│ StartupData     │  │ EffectResult    │  │ SkillService    │
│ skill_parser    │  │ ScriptInterp.   │  │ + MonsterRepo   │
│ skills.sql x2   │  │ ItemService     │  │                 │
│ README          │  │ Tests           │  │                 │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         └───────────────────┬┘───────────────────-─┘
                    Commit de integração
```

**Zero conflitos:** nenhum arquivo é tocado por mais de um agente.

**Dependência leve:** Agente 3 usa `EffectResult.damage()` que o Agente 2 cria. Como é adição pura (não quebra nada), ambos podem rodar em paralelo — Agente 3 compila contra o `damage` já existente no record.

## Fora do Escopo

- Custo real de SP por skill/nível (fixo em 10 SP por ora)
- Skills de dano sem campo `Script` no rAthena (ex: `MG_FIREBOLT`) — ficam como `unsupported()`
- Sistema de status effects (`sc_start`) — placeholder, implementação futura
- Reimportação automática de `items` via código — usuário faz `DELETE FROM items` manualmente
