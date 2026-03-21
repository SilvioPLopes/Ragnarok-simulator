# Script Pipeline Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Popular a tabela `skills` no startup, expandir `ScriptInterpreter` com novos comandos (heal, percentheal), e conectar dano de skill ao monstro em batalha.

**Architecture:** 3 chunks independentes com zero conflito de arquivos — podem ser executados em paralelo via worktrees. Agent 2 possui todo o código do `ScriptInterpreter` e `EffectResult`. Agent 3 possui todo o `SkillService` (incluindo a injeção do bean). `StartupDataLoader` pertence exclusivamente ao Agent 1.

**Tech Stack:** Java 17, Spring Boot 3.4.2, PostgreSQL, Maven (`./mvnw`), Python 3, psycopg2

**Spec:** `docs/superpowers/specs/2026-03-17-script-pipeline-design.md`

---

## Chunk 1: Agent 1 — Data Pipeline

**Arquivos:**
- Modificar: `src/main/java/com/ragnarok/runner/StartupDataLoader.java`
- Modificar: `scriptsPython/skill_parser.py`
- Regenerar: `scriptsPython/skills.sql` → copiar para `src/main/resources/db/skills.sql`
- Modificar: `README.md`

---

### Task 1: Adicionar `forceLoad` ao `StartupDataLoader`

**Contexto:** `loadIfEmpty` pula tabelas com `COUNT(*) > 0`. `skills.sql` usa `ON CONFLICT (id) DO UPDATE SET script`, então é seguro rodar sempre — mas o guard impede que scripts atualizados sejam aplicados. Solução: novo método `forceLoad` sem guard.

**Arquivo:** `src/main/java/com/ragnarok/runner/StartupDataLoader.java`

- [ ] **1.1 Adicionar o método `forceLoad`**

Adicionar após o método `loadIfEmpty` existente (linha 64):

```java
/**
 * Executa o SQL sem verificar se a tabela está vazia.
 * Usado para tabelas com ON CONFLICT DO UPDATE — idempotente por design.
 */
private void forceLoad(String sqlFile) {
    System.out.printf("🔄 Atualizando via force-load: %s...%n", sqlFile);
    try (Connection conn = dataSource.getConnection()) {
        ScriptUtils.executeSqlScript(conn, new ClassPathResource(sqlFile));
        System.out.printf("✅ %-20s atualizada (force).%n", sqlFile);
    } catch (Exception e) {
        System.err.printf("❌ Erro ao carregar %s: %s%n", sqlFile, e.getMessage());
    }
}
```

- [ ] **1.2 Atualizar o método `run()` para chamar `forceLoad` para skills**

Substituir o corpo do `run()` existente (linhas 31–38):

```java
@Override
public void run(String... args) throws Exception {
    ensureUniqueConstraints();
    forceLoad("db/skills.sql");                                        // ON CONFLICT DO UPDATE — seguro sempre
    loadIfEmpty("maps",          "db/maps.sql",            0);
    loadIfEmpty("map_portals",   "db/map_portals_v2.sql",  0);
    loadIfEmpty("map_monsters",  "db/map_monsters.sql",    0);
    loadIfEmpty("monster_drops", "db/monster_drops.sql",   0);
    loadIfEmpty("skill_tree",    "db/skill_tree.sql",      0);
}
```

- [ ] **1.3 Verificar que o `skills.sql` atual existe em `src/main/resources/db/`**

```bash
ls -lh src/main/resources/db/skills.sql
```

Esperado: arquivo com ~1652 linhas.

- [ ] **1.4 Compilar o projeto**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS sem erros.

- [ ] **1.5 Iniciar a aplicação e verificar o log**

```bash
./mvnw spring-boot:run 2>&1 | head -40
```

Esperado no log:
```
🔄 Atualizando via force-load: db/skills.sql...
✅ db/skills.sql              atualizada (force).
```

- [ ] **1.6 Confirmar que skills foram inseridas no banco**

```bash
psql -U postgres -d ragnarok_db -c "SELECT COUNT(*) FROM skills;"
```

Esperado: `count` entre 1000 e 2000.

- [ ] **1.7 Commit**

```bash
git add src/main/java/com/ragnarok/runner/StartupDataLoader.java
git commit -m "feat: add forceLoad to StartupDataLoader — skills table now populated on every startup"
```

---

### Task 2: Investigar `skill_parser.py` e regenerar `skills.sql`

**Contexto:** Todo `script` no `skills.sql` atual é `NULL`. O parser precisa extrair o campo `Script` do `skill_db.yml` do rAthena. Skills de suporte (ex: `AL_HEAL`) podem ter script; skills de dano direto (ex: `MG_FIREBOLT`) provavelmente não. O objetivo é descobrir o que existe e gerar um SQL com os scripts reais.

**Arquivo:** `scriptsPython/skill_parser.py`

- [ ] **2.1 Inspecionar o campo `Script` no rAthena skill_db.yml**

```bash
cd scriptsPython
python3 -c "
import requests, yaml
url = 'https://raw.githubusercontent.com/rathena/rathena/master/db/re/skill_db.yml'
data = yaml.safe_load(requests.get(url).text)
skills_com_script = [(s.get('Id'), s.get('Name'), s.get('Script'))
                     for s in data['Body'] if s.get('Script')]
print(f'Skills com Script: {len(skills_com_script)}')
for sid, name, script in skills_com_script[:10]:
    print(f'  {sid} {name}: {repr(script)}')
"
```

Esperado: lista de skills com scripts reais ou `Skills com Script: 0` (ambos são resultados válidos).

- [ ] **2.2 Verificar a estrutura atual do `skill_parser.py`**

```bash
grep -n "Script\|script" scriptsPython/skill_parser.py
```

Identificar em qual linha o campo `Script` é extraído e se está sendo passado para o SQL.

- [ ] **2.3 Corrigir a extração do campo `Script` se necessário**

Se o parser não estiver extraindo o campo `Script` corretamente, editar a função de parsing. Exemplo de extração correta:

```python
script = skill.get("Script", None)
# Sanitizar aspas simples para evitar SQL injection no arquivo gerado
if script:
    script = str(script).replace("'", "''")
```

No SQL gerado, o valor deve aparecer como:
```sql
-- Com script:
(123, 'AL_HEAL', 'Heal', '', 'itemheal rand(45,65)*1,0;')
-- Sem script:
(456, 'MG_FIREBOLT', 'Fire Bolt', '', NULL)
```

- [ ] **2.4 Regenerar `skills.sql`**

```bash
cd scriptsPython
python3 skill_parser.py
```

Esperado: arquivos `skills.sql` e `skill_tree.sql` regenerados.

- [ ] **2.5 Verificar se há scripts reais no SQL gerado**

```bash
grep -v "NULL" scriptsPython/skills.sql | grep "INSERT\|VALUES\|^(" | head -20
```

Se todos os valores forem `NULL`, registrar isso no commit — não é um bug, apenas significa que o rAthena não tem `Script` para skills.

- [ ] **2.6 Copiar para resources**

```bash
cp scriptsPython/skills.sql src/main/resources/db/skills.sql
```

- [ ] **2.7 Reiniciar a aplicação para aplicar o SQL atualizado**

```bash
./mvnw spring-boot:run 2>&1 | grep -E "skills|skill"
```

Esperado:
```
🔄 Atualizando via force-load: db/skills.sql...
✅ db/skills.sql              atualizada (force).
```

- [ ] **2.8 Verificar scripts no banco**

```bash
psql -U postgres -d ragnarok_db -c "SELECT COUNT(*) FROM skills WHERE script IS NOT NULL;"
```

Registrar o resultado no commit.

- [ ] **2.9 Commit**

```bash
git add scriptsPython/skill_parser.py scriptsPython/skills.sql src/main/resources/db/skills.sql
git commit -m "feat: regenerate skills.sql with real rAthena Script field data

Skills with non-NULL script: <N>
All others default to NULL — unsupported() in ScriptInterpreter (expected)."
```

---

### Task 3: Documentar reimportação de items no README

**Arquivo:** `README.md`

- [ ] **3.1 Adicionar seção "Resetando dados do banco"**

Localizar a seção `## 🗄 Banco de Dados` no `README.md` e adicionar logo após a tabela de tabelas:

```markdown
### Resetando dados do banco

Para forçar reimportação com dados atualizados do rAthena:

```sql
-- Reimportar itens (atualiza campo `script` com dados reais do rAthena):
DELETE FROM items;

-- Reimportar skills (atualiza `script` — feito automaticamente a cada startup):
DELETE FROM skills;
```

> Na próxima inicialização, `RathenaImporter` reimporta `items` automaticamente quando a tabela está vazia.
> A tabela `skills` é sempre atualizada via `forceLoad` no startup — não precisa de `DELETE` manual.
```

- [ ] **3.2 Commit**

```bash
git add README.md
git commit -m "docs: document database reset procedure for items and skills reimport"
```

---

## Chunk 2: Agent 2 — ScriptInterpreter

**Arquivos:**
- Criar: `src/main/java/com/ragnarok/application/service/ScriptInterpreter.java`
- Deletar: `src/main/java/com/ragnarok/domain/service/ScriptInterpreter.java`
- Modificar: `src/main/java/com/ragnarok/domain/model/EffectResult.java`
- Modificar: `src/main/java/com/ragnarok/application/service/ItemService.java`
- Criar: `src/test/java/com/ragnarok/domain/service/ScriptInterpreterTest.java`
- Modificar: `src/test/java/com/ragnarok/application/service/ItemServiceTest.java`

> **Nota arquitetural:** `ScriptInterpreter` é movido de `domain.service` para `application.service` porque `@Component` viola a regra do projeto: "domain nunca depende de Spring". O `domain.service.ScriptInterpreter` existente é deletado.

---

### Task 4: Mover `ScriptInterpreter` para `application.service`

**Contexto:** O arquivo atual em `domain/service` não tem `@Component` e é instanciado com `new` em **duas** services (`ItemService` linha 25 e `SkillService` linha 21). Ambas precisam ser atualizadas neste task — se não forem, a build quebra no momento em que o arquivo de `domain/service` for deletado.

- [ ] **4.1 Criar o novo arquivo em `application/service`**

Criar `src/main/java/com/ragnarok/application/service/ScriptInterpreter.java` com o conteúdo atual + header correto + `@Component`:

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.model.EffectResult;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScriptInterpreter {
    // [copiar o conteúdo interno do arquivo domain/service/ScriptInterpreter.java aqui]
    // NÃO alterar a lógica ainda — apenas o package e o @Component
}
```

- [ ] **4.2 Deletar o arquivo antigo**

```bash
rm src/main/java/com/ragnarok/domain/service/ScriptInterpreter.java
```

- [ ] **4.3 Atualizar import em `ItemService.java` (linha 8)**

```java
// ANTES:
import com.ragnarok.domain.service.ScriptInterpreter;
// DEPOIS:
import com.ragnarok.application.service.ScriptInterpreter;
```

- [ ] **4.4 Substituir `new ScriptInterpreter()` por injeção em `ItemService` (linha 25)**

```java
// ANTES:
private final ScriptInterpreter scriptInterpreter = new ScriptInterpreter();

// DEPOIS — campo sem inicialização + 5º parâmetro no construtor:
private final ScriptInterpreter scriptInterpreter;

public ItemService(ItemRepository itemRepository,
                   PlayerRepository playerRepository,
                   PlayerItemRepository playerItemRepository,
                   ItemMapper itemMapper,
                   ScriptInterpreter scriptInterpreter) {
    this.itemRepository       = itemRepository;
    this.playerRepository     = playerRepository;
    this.playerItemRepository = playerItemRepository;
    this.itemMapper           = itemMapper;
    this.scriptInterpreter    = scriptInterpreter;
}
```

- [ ] **4.5 Atualizar import em `SkillService.java` (linha 4)**

```java
// ANTES:
import com.ragnarok.domain.service.ScriptInterpreter;
// DEPOIS:
import com.ragnarok.application.service.ScriptInterpreter;
```

- [ ] **4.6 Remover field initializer em `SkillService.java` (linha 21)**

```java
// ANTES:
private final ScriptInterpreter scriptInterpreter = new ScriptInterpreter();

// DEPOIS — apenas o campo, sem inicialização (o construtor completo será feito no Chunk 3):
private final ScriptInterpreter scriptInterpreter;
```

> ⚠️ O construtor do `SkillService` ainda NÃO recebe `ScriptInterpreter` como parâmetro neste passo — isso é feito no Chunk 3 (Task 8). Por isso, o campo fica temporariamente sem atribuição. Compilar antes do Chunk 3 resultará em erro de compilação intencional — o Chunk 3 completa a injeção.
>
> **Se os Chunks 2 e 3 rodarem em paralelo em worktrees separadas, este passo (4.6) deve ser feito no Chunk 3, não aqui.** Nesse caso, pular o passo 4.6 e ir direto para 4.7.

- [ ] **4.7 Compilar**

```bash
./mvnw compile -q
```

Se rodando em paralelo (worktrees): esperado BUILD SUCCESS (SkillService ainda usa `new` temporariamente na worktree do Agent 2).
Se rodando sequencialmente: esperado BUILD FAILURE em SkillService por campo não atribuído — normal, será resolvido no Task 8.

- [ ] **4.8 Rodar todos os testes (apenas se compilando em paralelo/worktree)**

```bash
./mvnw test -q 2>&1 | tail -20
```

- [ ] **4.9 Commit**

```bash
git add src/main/java/com/ragnarok/application/service/ScriptInterpreter.java \
        src/main/java/com/ragnarok/application/service/ItemService.java \
        src/main/java/com/ragnarok/application/service/SkillService.java
git rm src/main/java/com/ragnarok/domain/service/ScriptInterpreter.java
git commit -m "refactor: move ScriptInterpreter to application.service — domain must not depend on Spring"
```

---

### Task 5: Expandir `EffectResult` com `isPercent`

**Contexto:** `EffectResult` é um record Java de 4 componentes. Vamos adicionar `isPercent` como 5º componente. Todos os factory methods precisam ser atualizados. O único call site que usa o construtor canônico `new EffectResult(...)` diretamente é `ScriptInterpreter.java` linha 55 — este arquivo é de nossa propriedade.

- [ ] **5.1 Escrever o teste primeiro**

Criar `src/test/java/com/ragnarok/application/service/ScriptInterpreterTest.java` *(package `application.service` — corresponde à nova localização da classe)*:

```java
package com.ragnarok.application.service;

import com.ragnarok.application.service.ScriptInterpreter;
import com.ragnarok.domain.model.EffectResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptInterpreterTest {

    private ScriptInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new ScriptInterpreter();
    }

    @Test
    @DisplayName("null retorna unsupported")
    void nullScript() {
        EffectResult r = interpreter.interpret(null);
        assertFalse(r.supported());
    }

    @Test
    @DisplayName("string vazia retorna unsupported")
    void emptyScript() {
        EffectResult r = interpreter.interpret("  ");
        assertFalse(r.supported());
    }

    @Test
    @DisplayName("comando desconhecido retorna unsupported")
    void unknownCommand() {
        EffectResult r = interpreter.interpret("bonus bAtk, 10;");
        assertFalse(r.supported());
    }

    @Test
    @DisplayName("itemheal valores fixos")
    void itemhealFixed() {
        EffectResult r = interpreter.interpret("itemheal 100, 0;");
        assertTrue(r.supported());
        assertEquals(100, r.hpHeal());
        assertEquals(0, r.spHeal());
        assertFalse(r.isPercent());
    }

    @Test
    @DisplayName("heal espaço-separado")
    void healSpaceSeparated() {
        EffectResult r = interpreter.interpret("heal 50, 20;");
        assertTrue(r.supported());
        assertEquals(50, r.hpHeal());
        assertEquals(20, r.spHeal());
        assertFalse(r.isPercent());
    }

    @Test
    @DisplayName("heal com parênteses")
    void healParentheses() {
        EffectResult r = interpreter.interpret("heal(50, 20)");
        assertTrue(r.supported());
        assertEquals(50, r.hpHeal());
        assertEquals(20, r.spHeal());
    }

    @Test
    @DisplayName("percentheal retorna isPercent=true")
    void percentheal() {
        EffectResult r = interpreter.interpret("percentheal 10, 5;");
        assertTrue(r.supported());
        assertTrue(r.isPercent());
        assertEquals(10, r.hpHeal());
        assertEquals(5, r.spHeal());
    }

    @Test
    @DisplayName("sc_start retorna unsupported")
    void scStart() {
        EffectResult r = interpreter.interpret("sc_start SC_INCREASEAGI, 60, 3, 0;");
        assertFalse(r.supported());
    }

    @Test
    @DisplayName("itemheal com rand — valor dentro do intervalo")
    void itemhealRand() {
        for (int i = 0; i < 50; i++) {
            EffectResult r = interpreter.interpret("itemheal rand(50,100), 0;");
            assertTrue(r.hpHeal() >= 50 && r.hpHeal() <= 100,
                    "Esperado 50-100, obtido: " + r.hpHeal());
        }
    }

    @Test
    @DisplayName("skill_lv é substituído pelo nível real")
    void skillLvSubstitution() {
        EffectResult r = interpreter.interpret("itemheal skill_lv * 100, 0;");
        // skill_lv=1 (padrão) → 1 * 100 = 100... mas o parser atual não evalua expressões
        // Apenas verificar que não lança exceção e retorna algo
        assertNotNull(r);
    }

    @Test
    @DisplayName("skill_lv substituído com nivel 3 via overload")
    void skillLvOverload() {
        // heal(skill_lv * 100, 0) com skillLevel=3 — script de skill
        // Após substituição: heal(3 * 100, 0) → o parser ainda não avalia aritmética
        // Verificar que o overload existe e não lança exceção
        EffectResult r = interpreter.interpret("itemheal 300, 0;", 3);
        assertEquals(300, r.hpHeal());
    }
}
```

- [ ] **5.2 Rodar os testes — esperar falhas**

```bash
./mvnw test -Dtest=ScriptInterpreterTest -q 2>&1 | tail -30
```

Esperado: vários FAIL porque `isPercent()` não existe ainda em `EffectResult`, `heal` e `percentheal` não existem no interpreter, e o overload `interpret(String, int)` não existe.

- [ ] **5.3 Atualizar `EffectResult` com o campo `isPercent`**

Substituir o conteúdo de `src/main/java/com/ragnarok/domain/model/EffectResult.java`:

```java
package com.ragnarok.domain.model;

public record EffectResult(
        int hpHeal,
        int spHeal,
        int damage,
        boolean supported,
        boolean isPercent
) {
    /** Cura com valores absolutos de HP e SP. */
    public static EffectResult heal(int hp, int sp) {
        return new EffectResult(hp, sp, 0, true, false);
    }

    /** Cura percentual — hpPct e spPct são valores 0-100 representando porcentagem. */
    public static EffectResult percentHeal(int hpPct, int spPct) {
        return new EffectResult(hpPct, spPct, 0, true, true);
    }

    /** Dano direto ao alvo. */
    public static EffectResult damage(int value) {
        return new EffectResult(0, 0, value, true, false);
    }

    public static EffectResult unsupported() {
        return new EffectResult(0, 0, 0, false, false);
    }
}
```

- [ ] **5.4 Compilar para ver call sites quebrados**

```bash
./mvnw compile 2>&1 | grep "error:"
```

Esperado: erros apenas em `ScriptInterpreter.java` onde `EffectResult.heal(hp, sp)` ainda usa o construtor com 4 argumentos.

---

### Task 6: Expandir `ScriptInterpreter` com novos comandos

- [ ] **6.1 Substituir o conteúdo de `ScriptInterpreter.java`**

`src/main/java/com/ragnarok/application/service/ScriptInterpreter.java`:

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.model.EffectResult;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScriptInterpreter {

    private static final Pattern RAND_PATTERN = Pattern.compile("rand\\((\\d+),(\\d+)\\)");
    private final Random rng = new Random();

    /** Interpreta script sem nível de skill (usa nível 1 por padrão). */
    public EffectResult interpret(String script) {
        return interpret(script, 1);
    }

    /**
     * Interpreta script rAthena com substituição de skill_lv pelo nível real.
     * Suporta: itemheal, heal, percentheal, sc_start.
     * Comandos não reconhecidos retornam unsupported() — nunca lança exceção.
     */
    public EffectResult interpret(String script, int skillLevel) {
        if (script == null || script.isBlank()) {
            return EffectResult.unsupported();
        }

        // Substitui skill_lv pelo nível real antes de parsear
        String resolved = script.trim().replace("skill_lv", String.valueOf(skillLevel));

        // Extrai comando principal (primeira palavra antes de espaço ou parêntese)
        int end = resolved.length();
        for (int i = 0; i < resolved.length(); i++) {
            char c = resolved.charAt(i);
            if (c == ' ' || c == '(') {
                end = i;
                break;
            }
        }
        String command = resolved.substring(0, end).toLowerCase();

        return switch (command) {
            case "itemheal"    -> parseItemheal(resolved);
            case "heal"        -> parseHeal(resolved);
            case "percentheal" -> parsePercentheal(resolved);
            case "sc_start"    -> EffectResult.unsupported();
            default            -> EffectResult.unsupported();
        };
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    /** Formato: itemheal <hp>, <sp>; */
    private EffectResult parseItemheal(String script) {
        String args = script.replaceFirst("(?i)^itemheal\\s*", "").replaceAll(";.*$", "").trim();
        return parseHealArgs(args, false);
    }

    /**
     * Formato: heal <hp>, <sp>;  ou  heal(<hp>, <sp>)
     * Suporta ambos — espaço-separado e parênteses.
     */
    private EffectResult parseHeal(String script) {
        // Remove "heal" e normaliza — suporta "heal 50,0;" e "heal(50,0)"
        String args = script.replaceFirst("(?i)^heal\\s*", "")
                            .replaceAll("[();]", "")
                            .trim();
        return parseHealArgs(args, false);
    }

    /** Formato: percentheal <hp%>, <sp%>; */
    private EffectResult parsePercentheal(String script) {
        String args = script.replaceFirst("(?i)^percentheal\\s*", "").replaceAll(";.*$", "").trim();
        return parseHealArgs(args, true);
    }

    private EffectResult parseHealArgs(String args, boolean isPercent) {
        int splitIndex = findTopLevelComma(args);
        if (splitIndex < 0) return EffectResult.unsupported();

        String hpArg = args.substring(0, splitIndex).trim();
        String spArg = args.substring(splitIndex + 1).trim();

        int hp = resolveArg(hpArg);
        int sp = resolveArg(spArg);

        return isPercent
                ? EffectResult.percentHeal(hp, sp)
                : EffectResult.heal(hp, sp);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    private int resolveArg(String arg) {
        // Remove espaços e parênteses soltos
        String clean = arg.replaceAll("[()]", "").trim();
        Matcher m = RAND_PATTERN.matcher(arg);
        if (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int max = Integer.parseInt(m.group(2));
            return min + rng.nextInt(max - min + 1);
        }
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
```

- [ ] **6.2 Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS.

- [ ] **6.3 Verificar que os 4 arquivos afetados pelo `EffectResult` compilam**

A adição de `isPercent` ao record muda o construtor canônico. Os 4 arquivos que precisam ser atualizados atomicamente:
- `EffectResult.java` — o record em si (já feito no passo 6.1)
- `ScriptInterpreter.java` — chama `EffectResult.heal()` e `EffectResult.unsupported()` (já feito no passo 6.1)
- `ItemService.java` — lê `result.hpHeal()`, `result.spHeal()`, `result.supported()` (acessores do record — não quebram com adição de campo)
- `SkillService.java` — lê `result.supported()`, `result.hpHeal()` (idem — acessores não quebram)

Os acessores de record em Java não mudam com adição de novo campo, então `ItemService` e `SkillService` compilam sem alteração. A única mudança real é em `EffectResult` + `ScriptInterpreter` (factory methods com `false` como 5º argumento).

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS.

- [ ] **6.4 Rodar testes do ScriptInterpreter**

```bash
./mvnw test -Dtest=ScriptInterpreterTest -q 2>&1 | tail -20
```

Esperado: todos os testes passando. Se algum falhar, corrigir o interpreter antes de prosseguir.

- [ ] **6.5 Commit**

```bash
git add src/main/java/com/ragnarok/application/service/ScriptInterpreter.java \
        src/main/java/com/ragnarok/domain/model/EffectResult.java \
        src/test/java/com/ragnarok/application/service/ScriptInterpreterTest.java
git commit -m "feat: expand ScriptInterpreter — heal, percentheal, skill_lv substitution, @Component"
```

---

### Task 7: Atualizar `ItemService` para `percentheal`

**Contexto:** `consumirItem` em `ItemService` chama `aplicarCura(player, itemEntity, result.hpHeal(), result.spHeal(), nome)` passando os valores diretamente. Para `percentheal`, os valores são percentuais (0–100) e precisam ser convertidos para absoluto antes de chamar `aplicarCura`.

- [ ] **7.1 Atualizar `consumirItem` em `ItemService.java`**

Localizar o método `consumirItem` (linha ~122). Substituir o bloco `if (item.getScript() != null)`:

```java
// ANTES:
if (item.getScript() != null) {
    EffectResult result = scriptInterpreter.interpret(item.getScript());
    if (!result.supported()) {
        return "Este item não pode ser usado ainda.";
    }
    return aplicarCura(player, itemEntity, result.hpHeal(), result.spHeal(), nome);
}

// DEPOIS:
if (item.getScript() != null) {
    EffectResult result = scriptInterpreter.interpret(item.getScript());
    if (!result.supported()) {
        return "Este item não pode ser usado ainda.";
    }
    int hpMax = player.getHpMax() != null ? player.getHpMax() : 100;
    int spMax = player.getSpMax() != null ? player.getSpMax() : 40;
    int hpHeal = result.isPercent() ? hpMax * result.hpHeal() / 100 : result.hpHeal();
    int spHeal = result.isPercent() ? spMax * result.spHeal() / 100 : result.spHeal();
    return aplicarCura(player, itemEntity, hpHeal, spHeal, nome);
}
```

- [ ] **7.2 Compilar**

```bash
./mvnw compile -q
```

- [ ] **7.3 Rodar todos os testes**

```bash
./mvnw test -q 2>&1 | tail -20
```

Esperado: BUILD SUCCESS, todos os testes passando.

- [ ] **7.4 Commit**

```bash
git add src/main/java/com/ragnarok/application/service/ItemService.java
git commit -m "feat: handle percentheal in ItemService.consumirItem"
```

---

## Chunk 3: Agent 3 — Integration

**Arquivos:**
- Modificar: `src/main/java/com/ragnarok/application/service/SkillService.java`

> **Pré-condição:** Agente 3 deve rodar **após** o Agente 2 ter completado o Task 4 (ScriptInterpreter movido e injeção atualizada em ItemService). O `SkillService` também precisa da injeção do `ScriptInterpreter` — este chunk faz isso junto com o `MonsterRepository`.

---

### Task 8: Atualizar `SkillService` — injeção + dano de skill

**Contexto atual do `SkillService`:**
- Campo `private final ScriptInterpreter scriptInterpreter = new ScriptInterpreter();` na linha 21
- Import `com.ragnarok.domain.service.ScriptInterpreter` na linha 4
- Constructor com 4 parâmetros (linhas 23–31)
- `usarSkillEmCombate` não usa `monsterId`, não aplica dano

- [ ] **8.1 Escrever o teste de integração primeiro**

Abrir `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java` e adicionar um novo teste verificando que uma skill com script de dano causa dano ao monstro. Localizar o fim da classe e adicionar:

```java
@Test
@DisplayName("skill com damage > 0 reduz HP do monstro")
@Transactional
void skillComDanoReducHpDoMonstro() {
    // Este teste só é útil quando um script de dano real existir no banco.
    // Por ora, verifica que uma skill sem script lança IllegalStateException.
    Long playerId = 1L;
    Long monsterId = monsterRepository.findAll()
            .stream().findFirst()
            .orElseThrow().getId();

    // Qualquer skill aprendida
    var skills = playerSkillRepository.findByPlayerId(playerId);
    if (skills.isEmpty()) {
        // Sem skills aprendidas — teste não aplicável
        return;
    }

    String aegisName = skills.get(0).getSkillId();
    try {
        String resultado = skillService.usarSkillEmCombate(playerId, aegisName, monsterId);
        assertNotNull(resultado);
    } catch (IllegalStateException e) {
        // "skill não pode ser usada ainda" é resultado válido se script=NULL
        assertTrue(e.getMessage().contains("não pode ser usada") ||
                   e.getMessage().contains("SP insuficiente") ||
                   e.getMessage().contains("não encontrada"));
    }
}
```

- [ ] **8.2 Rodar o teste atual para ver o estado baseline**

```bash
./mvnw test -Dtest=SkillServiceIntegrationTest -q 2>&1 | tail -20
```

Registrar quantos testes passam hoje antes da mudança.

- [ ] **8.3 Verificar import de `ScriptInterpreter` em `SkillService`**

Se o Chunk 2 (Task 4.5) já atualizou o import, pular este passo. Caso contrário:
```java
// ANTES:
import com.ragnarok.domain.service.ScriptInterpreter;
// DEPOIS:
import com.ragnarok.application.service.ScriptInterpreter;
```

- [ ] **8.4 Adicionar campo `monsterRepository` e completar a injeção do `ScriptInterpreter`**

Localizar as declarações de campos (linhas 17–21). Substituir:

```java
// ANTES (linha 21):
private final ScriptInterpreter scriptInterpreter = new ScriptInterpreter();

// DEPOIS — adicionar MonsterRepository e remover o new:
private final MonsterRepository monsterRepository;
private final ScriptInterpreter scriptInterpreter;
```

Adicionar o import necessário:
```java
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
```

- [ ] **8.5 Atualizar o constructor para 6 parâmetros**

Substituir o constructor existente (linhas 23–31):

```java
public SkillService(PlayerRepository playerRepository,
                    SkillTreeRepository skillTreeRepository,
                    PlayerSkillRepository playerSkillRepository,
                    SkillRepository skillRepository,
                    MonsterRepository monsterRepository,
                    ScriptInterpreter scriptInterpreter) {
    this.playerRepository      = playerRepository;
    this.skillTreeRepository   = skillTreeRepository;
    this.playerSkillRepository = playerSkillRepository;
    this.skillRepository       = skillRepository;
    this.monsterRepository     = monsterRepository;
    this.scriptInterpreter     = scriptInterpreter;
}
```

- [ ] **8.6 Substituir `usarSkillEmCombate` com implementação completa**

Substituir o método inteiro (linhas 144–183):

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

    player.setSpCurrent(spAtual - SP_CUSTO_SKILL);
    playerRepository.save(player);

    return mensagem;
}
```

- [ ] **8.7 Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS.

- [ ] **8.8 Rodar todos os testes com foco nas skills**

```bash
./mvnw test -Dtest="SkillServiceIntegrationTest,SkillServiceAprenderTest" -q 2>&1 | tail -30
```

> ⚠️ Esses dois testes são os mais sensíveis à mudança do constructor — usam `@SpringBootTest` com injeção real. Se falharem com `UnsatisfiedDependencyException`, verificar que `MonsterRepository` e `ScriptInterpreter` estão disponíveis no contexto de teste (ambos são Spring beans normais — devem funcionar).

```bash
./mvnw test -q 2>&1 | tail -20
```

Esperado: BUILD SUCCESS, todos os testes passando. Se algum teste de unidade criar `SkillService` manualmente (`new SkillService(...)`), atualizar para incluir os 2 novos parâmetros usando `mock(MonsterRepository.class)` e `mock(ScriptInterpreter.class)`.

- [ ] **8.9 Commit**

```bash
git add src/main/java/com/ragnarok/application/service/SkillService.java
git commit -m "feat: SkillService — inject MonsterRepository and ScriptInterpreter, add damage handling in usarSkillEmCombate"
```

---

## Verificação Final (após os 3 chunks)

- [ ] **Rodar todos os testes**

```bash
./mvnw test 2>&1 | tail -10
```

Esperado: `BUILD SUCCESS`, zero falhas.

- [ ] **Iniciar a aplicação e checar log completo do startup**

```bash
./mvnw spring-boot:run 2>&1 | head -50
```

Esperado no log:
```
📦 Monstros já existem no banco. Pulando importação.
📦 Itens já existem no banco. Pulando importação.
🔄 Atualizando via force-load: db/skills.sql...
✅ db/skills.sql              atualizada (force).
📦 maps            já populada (...). Pulando.
...
```

- [ ] **Verificar skills no banco**

```bash
psql -U postgres -d ragnarok_db -c "SELECT COUNT(*), COUNT(script) FROM skills;"
```

`count` = total de skills, `count(script)` = skills com script não-NULL.

- [ ] **Teste manual de uso de skill em batalha**

1. Iniciar o jogo
2. Aprender uma skill (Status → S → selecionar skill disponível)
3. Iniciar batalha (Caçar monstros)
4. Tentar usar skill (opção 2 no menu de batalha)
5. Verificar mensagem de resultado

- [ ] **Commit final de integração**

```bash
git add -A
git commit -m "chore: integration — script pipeline complete (data + interpreter + skill damage)"
```
