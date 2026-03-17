# Design: Consumíveis HP/SP + Stats Derivados na Tela de Status

**Data:** 2026-03-11
**Branch:** feature/Alt-02
**Status:** Aprovado

---

## Objetivo

1. Corrigir `ItemService.usarItem()` para que consumíveis recuperem HP e/ou SP.
2. Exibir stats derivados de combate (ATK, MATK, DEF, HIT, FLEE) na tela de status do terminal.

---

## Feature 1 — Fix `consumirItem()` (HP + SP)

### Problema

`consumirItem()` em `ItemService` usa o campo `efeito` apenas para curar HP. SP nunca é recuperado. O campo `bonusSP` já existe em `ItemStats` (domínio) mas não em `ItemEntity` (persistência), portanto não é persistido nem mapeado.

### Abordagem Escolhida

Usar `efeito` como amount de cura de HP e `bonusSP` como amount de cura de SP. Uma poção pode curar HP, SP, ou ambos simultaneamente.

### Mudanças

#### `ItemEntity` — novo campo
```java
@Column(name = "bonus_sp")
private Integer bonusSp;
```
O `ddl-auto: update` cria a coluna automaticamente no próximo start. Dados existentes ficam `null` (tratados como 0 — sem regressão).

#### `ItemEntity.getStats()` — mapear novo campo
```java
statsObj.setBonusSP(this.bonusSp);
```

#### `ItemService.consumirItem()` — nova lógica
```
Se efeito > 0:
    novoHp = min(hpMax, hpAtual + efeito)
    player.setHpCurrent(novoHp)
    hpCurado = novoHp - hpAtual

Se bonusSP > 0:
    novoSp = min(spMax, spAtual + bonusSP)
    player.setSpCurrent(novoSp)
    spCurado = novoSp - spAtual

Se nenhum > 0:
    retorna "Este item não tem efeito ao ser usado."

Se HP cheio e SP cheio (quando ambos aplicáveis):
    retorna "HP e SP já estão cheios!"

Salvar player, consumir item (amount-- ou delete)
Retornar mensagem descritiva com o que foi curado
```

#### Mensagens de retorno (exemplos)
- `"Você usou Red Potion e recuperou 45 de HP."`
- `"Você usou Blue Potion e recuperou 30 de SP."`
- `"Você usou Yggdrasil Berry e recuperou 500 de HP e 200 de SP."`

---

## Feature 2 — Stats Derivados no Domínio e Tela de Status

### Problema

`renderStatusMenu()` exibe apenas stats base (STR/AGI/VIT/INT/DEX/LUK) com bônus de equipamentos. Stats de combate derivados (ATK, MATK, DEF, HIT, FLEE) não são exibidos.

### Abordagem Escolhida

Adicionar métodos de stats derivados em `Player` (domínio). Exibir na `renderStatusMenu()` do terminal runner. Os métodos serão reutilizáveis futuramente pela `BattleEngine` e pelo sistema de elemental damage (roadmap).

### Fórmulas (clássicas RO)

| Stat | Fórmula |
|------|---------|
| ATK  | `getTotalStr() * 2 + WeaponATK (equipada)` |
| MATK | `getTotalInt() * 2 + WeaponMATK (equipada)` |
| DEF  | `getTotalVit()` |
| HIT  | `getTotalDex() + baseLevel` |
| FLEE | `getTotalAgi() + baseLevel` |

### Mudanças

#### `Player.java` — `getMaxSp()` + 5 novos métodos

Análogo ao `getMaxHp()` já existente:
```java
public Integer getMaxSp() {
    return this.stats != null && this.stats.getMaxSp() != null ? this.stats.getMaxSp() : 40;
}
```

#### `Player.java` — 5 novos métodos de stats derivados
- `getTotalAtk()` — STR*2 + soma do `attack` dos equipamentos
- `getTotalMAtk()` — INT*2 + soma do `mAttack` dos equipamentos
- `getTotalDef()` — delega para `getTotalVit()`
- `getTotalHit()` — `getTotalDex() + baseLevel`
- `getTotalFlee()` — `getTotalAgi() + baseLevel`

#### `renderStatusMenu()` em `RagnarokTerminalRunner`
Adicionar bloco após os stats base e antes do EXP:

```
----------------------------
ATK : 42  | MATK: 20
DEF : 15  | HIT : 35
FLEE: 28  |
```

Nenhuma nova dependência de serviço — usa apenas `domainPlayer` (já carregado via `playerMapper.toDomain(p)`).

---

## Arquivos Afetados

| Arquivo | Mudança |
|---------|---------|
| `infrastructure/persistence/ItemEntity.java` | + campo `bonusSp` |
| `infrastructure/persistence/ItemEntity.java` | `getStats()` mapeia `bonusSP` |
| `application/service/ItemService.java` | `consumirItem()` trata HP e SP |
| `domain/model/Player.java` | + `getMaxSp()` (análogo ao `getMaxHp()` existente) |
| `domain/model/Player.java` | + 5 métodos de stats derivados |
| `runner/RagnarokTerminalRunner.java` | `renderStatusMenu()` exibe ATK/MATK/DEF/HIT/FLEE |

---

## Não incluído neste escopo

- Atualizar o `ItemDbParser` / `RathenaImporter` para extrair `bonus_sp` do YAML (dados de SP serão null/0 até que o parser seja atualizado).
- Persistir stats derivados nas colunas `hit`/`flee`/`def`/`mDef` de `PlayerEntity`.
- Modificar `BattleEngine` para usar os novos métodos (compatível mas não obrigatório agora).
