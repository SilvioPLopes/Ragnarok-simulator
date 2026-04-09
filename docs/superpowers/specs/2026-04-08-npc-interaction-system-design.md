# NPC Interaction System — Design Spec

**Date:** 2026-04-08
**Status:** Approved
**Scope:** Backend (ragnarok-core) + Frontend (ragnarok-front)

---

## Goal

Implementar interação completa com NPCs: diálogos paginados com balões, menus de escolha (select/menu rAthena), e ações (close, heal, warp, getitem). NPCs de loja mostram balão genérico antes de abrir a loja.

---

## Decisões de Design

| Decisão | Escolha | Motivo |
|---|---|---|
| Nível de interação | Sistema completo (dialog + menu + ações) | Cobre Kafra, shops, quest NPCs |
| Condicionais rAthena (`if/else`) | Ignoradas — segue primeiro branch | Simplificação consciente; ver Limitações |
| Storage | Tabela separada `npc_dialogs` com JSONB | Fácil de reprocessar sem tocar em `npcs` |
| Navegação | Stateless — frontend baixa árvore, navega localmente | Zero roundtrip por passo, árvore < 2KB |
| UI | Modal centralizado (padrão Shop/Warp existente) | Consistência visual |
| Shop NPCs | Balão genérico + menu sintético no frontend | Shops não têm script rAthena, só definição |

---

## Estrutura de Dados — Árvore de Diálogo

Cada NPC tem uma lista ordenada de nós. O frontend navega por índices.

### Tipos de nó

```json
// Nó de diálogo
{
  "type": "dialog",
  "speaker": "Shuger",
  "texts": ["Outside the city...", "Though it's cute..."],
  "next": 1
}

// Nó de menu (select/switch)
{
  "type": "menu",
  "texts": ["What do you want?"],
  "choices": [
    { "label": "Tell me more", "next": 2 },
    { "label": "Cancel",       "next": 4 }
  ]
}

// Nó de ação
{ "type": "action", "action": "close" }
{ "type": "action", "action": "heal" }
{ "type": "action", "action": "warp",    "params": { "map": "prontera", "x": 150, "y": 180 } }
{ "type": "action", "action": "getitem", "params": { "itemId": 2501, "amount": 1 } }
```

### Regras do parser

| Comando rAthena | Comportamento |
|---|---|
| `mes "texto"` | Acumula em `texts` do nó atual |
| `next` | Fecha nó atual (type=dialog), abre novo |
| `select("A:B")` | Nó tipo menu com choices |
| `switch(select(...))` | Mesmo tratamento que select |
| `close` / `close2` | Nó tipo action=close |
| `heal` | Nó tipo action=heal |
| `warp "map",x,y` | Nó tipo action=warp |
| `getitem itemId,amount` | Nó tipo action=getitem |
| `cutin` | Ignorado |
| `if/else` / condicionais | **Ignoradas — segue primeiro branch** |
| `duplicate()` / `warp` (linha) | Ignoradas (não são script blocks) |

---

## Banco de Dados

### V8 migration

```sql
CREATE TABLE npc_dialogs (
    id     BIGSERIAL PRIMARY KEY,
    npc_id BIGINT NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    nodes  JSONB  NOT NULL
);

CREATE UNIQUE INDEX idx_npc_dialogs_npc_id ON npc_dialogs(npc_id);
```

A coluna `dialog TEXT` em `npcs` **não é removida** — fica como fallback legado.

---

## Backend — Componentes

### Novos arquivos

| Arquivo | Tipo | Responsabilidade |
|---|---|---|
| `V8__npc_dialogs_table.sql` | Migration | Cria `npc_dialogs` |
| `NpcDialogEntity.java` | JPA Entity | Mapeia `npc_dialogs`; campo `nodes` como `@JdbcTypeCode(SqlTypes.JSON)` |
| `NpcDialogRepository.java` | Repository | `findByNpcId(Long)` → `Optional<NpcDialogEntity>` |
| `NpcDialogNode.java` | Sealed interface | `DialogNode`, `MenuNode`, `ActionNode` como records |
| `NpcDialogTreeDTO.java` | DTO | `record(List<Object> nodes)` — serializado como JSON puro |

### Arquivos modificados

| Arquivo | Mudança |
|---|---|
| `RathenaNpcScriptParser.java` | Novo método `parseFileToNodes(Path)` → `List<NpcDialogNode>`. Método atual mantido. |
| `RathenaDialogPopulator.java` | Salva em `npc_dialogs` em vez de `npcs.dialog`. DELETE + INSERT por NPC. |
| `NpcService.java` | `getDialog(npcId)` → busca em `npc_dialogs`, retorna `NpcDialogTreeDTO`. Se não há dialog tree, retorna `{"nodes": []}`. |
| `NpcController.java` | Sem mudança de assinatura — `GET /api/npcs/{npcId}/dialog` já existe. |
| `ClientDataRunner.java` | Sem mudança — `rathenaDialogPopulator.run()` já está no passo 9. |

### Endpoint

```
GET /api/npcs/{npcId}/dialog
→ 200 { "nodes": [...] }
→ 200 { "nodes": [] }   ← NPC sem diálogo
→ 404                    ← NPC não encontrado
```

---

## Frontend — Componentes

### Novo: `components/game/npc-dialog-modal.tsx`

```
Props:
  npc: NpcDTO
  onClose: () => void
  onOpenShop: () => void   ← chamado quando choice = "__shop__"

Estado local:
  nodes: NpcDialogNode[]
  currentIndex: number

Fluxo:
  1. Mount → GET /api/npcs/{npc.id}/dialog
  2. Se nodes vazio E type=NPC → fecha modal (NPC sem fala)
  3. Renderiza nó atual por tipo:
     - dialog → speaker + texts + botão "Próximo" (→ node.next)
     - menu   → texts + botões por choice (→ choice.next)
     - action=close → fecha modal automaticamente
     - action=heal  → chama npcApi.heal(), fecha modal
     - action=warp  → chama npcApi.warp(), fecha modal
     - action=getitem → mostra mensagem "Você recebeu X"
```

### Modificado: `components/game/city-panel.tsx`

**`handleNpcClick` por tipo:**

| Tipo | Comportamento atual | Novo comportamento |
|---|---|---|
| `NPC` | Toast "sem interação" | Abre `NpcDialogModal` |
| `SHOP` | Abre shop modal direto | Abre `NpcDialogModal` com árvore sintética; choice "Ver itens" → abre shop modal |
| `HEAL` | Chama heal direto | Sem mudança |
| `WARP` | Abre warp modal | Sem mudança |

**Árvore sintética para SHOP (gerada no frontend):**

```json
[
  {
    "type": "dialog",
    "speaker": "<npc.name>",
    "texts": ["Bem-vindo! O que deseja?"],
    "next": 1
  },
  {
    "type": "menu",
    "texts": [],
    "choices": [
      { "label": "Ver itens", "next": "__shop__" },
      { "label": "Fechar",    "next": "__close__" }
    ]
  }
]
```

---

## Limitações Conhecidas (Melhorias Futuras)

> Registradas para implementação futura — não bloqueiam o escopo atual.

1. **Condicionais rAthena não avaliadas** — scripts com `if (Class == Job_Novice)`, `if (Zeny < 20)` etc. sempre seguem o primeiro branch. Implementação futura: endpoint `POST /api/npcs/{id}/interact` que recebe `playerId` e avalia condicionais com base no estado do personagem.

2. **Ações de quest não implementadas** — `setquest`, `completequest`, `callsub`, `callfunc`, `doevent` são ignorados no parser. Implementação futura: sistema de quests com estado por player.

3. **`cutin` ignorado** — em RO, `cutin "kafra_05"` mostra uma imagem de personagem no canto da tela. Implementação futura: mapear cutin ids para assets e exibir no modal.

4. **`duplicate()` não carregam o script original** — NPCs duplicados (ex: Guards em prontera) não herdam o script do template. Implementação futura: resolver referências de duplicate no parser.

5. **Múltiplos NPCs em mesmas coordenadas** — `findFirstByMapNameAndXAndY` pega o primeiro. Implementação futura: match por nome + coordenadas.

---

## Não está no escopo

- Sistema de quests (tracking de progresso por player)
- `savepoint` (salvar respawn point via Kafra)
- Storage Kafra (inventário expandido)
- Cart Kafra (pushcart)
- Avaliação de variáveis de script rAthena (`@var`, `#var`, `$var`)
