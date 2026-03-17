# Design: Script Interpreter — Itens e Skills

**Data:** 2026-03-16
**Status:** Aprovado

---

## Problema

A tabela `items` tem um campo `script TEXT` (recém adicionado via ALTER TABLE) que está NULL para todos os registros porque o `ItemDbParser` ignorava o campo `Script` do YAML do rAthena.

A tabela `skills` também tem um campo `script TEXT` (recém adicionado) e está completamente vazia — o `skill_parser.py` não extraía nem o Script nem populava a tabela.

Sem esses dados, consumíveis não têm efeito e skills não podem ser usadas em batalha.

---

## Fonte dos Dados

O rAthena define efeitos de itens e skills via um campo `Script` em YAML:

```yaml
# db/re/item_db_usable.yml
- Id: 501
  Name: Red_Potion
  Script: "itemheal rand(45,65),0;"

- Id: 505
  Name: Blue_Potion
  Script: "itemheal 0,rand(40,60);"

- Id: 504
  Name: White_Potion
  Script: "itemheal rand(700,900),0;"
```

```yaml
# db/re/skill_db.yml
- Id: 28
  Name: AL_HEAL
  Description: Heal
  Script: "skill AL_HEAL,1;"

- Id: 1
  Name: NV_BASIC
  Description: Basic Skill
  Script: ~   # null — skill passiva
```

---

## Comandos rAthena Relevantes (escopo deste sprint)

Focar apenas nos comandos que aparecem em itens CONSUMABLE e skills de combat:

| Comando | Significado | Exemplo |
|---|---|---|
| `itemheal X,Y` | Cura X de HP e Y de SP | `itemheal rand(45,65),0;` |
| `itemheal rand(A,B),Y` | Cura HP aleatório entre A e B | `itemheal rand(700,900),0;` |
| `skill NAME,LV` | Ativa uma skill | `skill AL_HEAL,1;` |
| `sc_start SC_XXXX,...` | Aplica status/buff | `sc_start SC_BLESSING,240000,10;` |

**Fora do escopo agora:** `bonus`, `percentheal`, `unitskilluseid`, e outros comandos avançados — retornam `EffectResult.UNSUPPORTED`.

---

## Arquitetura

### Novo: `ScriptInterpreter` (domain/service)

Classe pura sem Spring, sem JPA. Recebe uma String de script e retorna um `EffectResult`.

```java
// domain/service/ScriptInterpreter.java
public class ScriptInterpreter {
    public EffectResult interpret(String script) { ... }
}
```

### Novo: `EffectResult` (domain/model)

Record simples que representa o resultado de interpretar um script:

```java
public record EffectResult(
    int hpHeal,      // HP a recuperar (0 se não aplicável)
    int spHeal,      // SP a recuperar (0 se não aplicável)
    int damage,      // Dano a causar (0 se não aplicável) — para skills ofensivas futuras
    boolean supported // false se o script não é interpretável ainda
) {
    public static EffectResult heal(int hp, int sp) {
        return new EffectResult(hp, sp, 0, true);
    }
    public static EffectResult unsupported() {
        return new EffectResult(0, 0, 0, false);
    }
}
```

### Lógica do `ScriptInterpreter`

```
1. Se script é null ou blank → retorna unsupported()
2. Extrai o comando principal (primeira palavra antes de espaço ou parentese)
3. Switch no comando:
   - "itemheal" → parsear dois argumentos (hp, sp)
     - Se argumento é "rand(A,B)" → sortear entre A e B inclusive
     - Se argumento é número fixo → usar direto
     - Retornar EffectResult.heal(hp, sp)
   - outros → retornar unsupported()
```

---

## Mudanças nos Arquivos Existentes

### 1. `ItemDbParser.java`

Adicionar extração do campo `Script` no método `toEntity()`:

```java
// Adicionar após entity.setPrice(...)
String script = (String) item.get("Script");
entity.setScript(script);  // null se não existir — OK
```

### 2. `ItemEntity.java`

Adicionar campo (a coluna já existe no banco via ALTER TABLE):

```java
@Column(name = "script", columnDefinition = "TEXT")
private String script;
```

### 3. `ItemService.java` — método `consumirItem()`

Lógica atual provavelmente cai no caminho de ETC para CONSUMABLE.

Nova lógica:
```
Se item.getType() == CONSUMABLE:
    Se item.getScript() não é null:
        EffectResult result = scriptInterpreter.interpret(item.getScript())
        Se result.supported():
            Aplicar hpHeal e spHeal no player (respeitando max)
            Consumir item (amount-- ou delete)
            Retornar mensagem descritiva
        Senão:
            Retornar "Este item não pode ser usado ainda."
    Senão se item.getEfeito() > 0:   ← fallback para os UPDATEs manuais que já existem
        Usar efeito como HP heal (comportamento legado)
    Senão:
        Retornar "Este item não tem efeito."
```

### 4. `skill_parser.py`

Adicionar extração do campo `Script` no `parsear_skills()`:

```python
# Adicionar no loop de skills
script = skill.get("Script", None)

skills.append({
    "id":         int(skill_id),
    "aegis_name": str(aegis_name),
    "name":       str(name),
    "type":       str(skill_type),
    "script":     str(script) if script else None,
})
```

Atualizar `gerar_skills_sql()` para incluir o campo `script` no INSERT:

```sql
INSERT INTO skills (id, aegis_name, name, type, script) VALUES ...
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    script = EXCLUDED.script;
```

### 5. `SkillService.java` — método `aprenderSkill()` / novo `usarSkillEmCombate()`

Criar método `usarSkillEmCombate(Long playerId, String aegisName, Long monsterId)`:

```
1. Buscar skill em SkillRepository por aegisName
2. Verificar se player aprendeu a skill (PlayerSkillRepository)
3. Verificar se player tem SP suficiente (custo fixo: 10 SP por enquanto)
4. Interpretar script da skill via ScriptInterpreter
5. Se result.supported() e result.hpHeal > 0 → aplicar cura no player
6. Se result.supported() e result.damage > 0 → aplicar dano no monstro (futuro)
7. Deduzir SP do player
8. Retornar mensagem descritiva
```

---

## Menu de Batalha — Mudanças no Terminal

### `RagnarokTerminalRunner.renderBattleMenu()`

Adicionar duas opções:

```
=== BATALHA ===
VOCE: HP 150/500 | SP 40/100
Poring: HP 50

1. Atacar
2. Usar Skill
3. Usar Item
4. Fugir
>
```

**Opção 2 — Usar Skill:**
- Lista skills aprendidas pelo player via `PlayerSkillRepository.findByPlayerId()`
- Player digita número da skill
- Chama `SkillService.usarSkillEmCombate()`
- Exibe resultado

**Opção 3 — Usar Item:**
- Lista consumíveis do inventário via `PlayerItemRepository.findByPlayerId()` filtrado por type CONSUMABLE
- Player digita número do item
- Chama `ItemService.usarItem(itemId)`
- Exibe resultado

---

## Ordem de Implementação

1. `EffectResult` record — sem dependências
2. `ScriptInterpreter` — sem dependências
3. `ItemEntity` + `ItemDbParser` — adicionar campo script
4. `ItemService.consumirItem()` — integrar ScriptInterpreter
5. `skill_parser.py` — extrair script e regenerar `skills.sql`
6. Rodar `python3 skill_parser.py` e `python3 Migrate.py --force` para popular skills com script
7. `SkillService.usarSkillEmCombate()` — integrar ScriptInterpreter
8. `RagnarokTerminalRunner` — adicionar opções 2 e 3 no menu de batalha

---

## Notas Importantes

- `RathenaImporter` reimporta itens quando `itemRepo.count() == 0`. Após a mudança, o campo `script` será populado automaticamente na próxima reimportação limpa.
- Os UPDATEs manuais de `efeito` que foram feitos anteriormente servem como **fallback** — não remover.
- A tabela `skills` precisa ser repopulada via `skill_parser.py` após a mudança — o script gera um novo `skills.sql` com o campo `script`.
- `ddl-auto=update` vai adicionar a coluna `script` em `ItemEntity` automaticamente se não existir — mas já existe via ALTER TABLE, sem conflito.
- Testes de integração devem usar banco `ragnarok_test` — ver `application-test.properties`.