Atue como Tech Lead Backend Java/Kotlin. O frontend já está correto, mas o POST /api/shop/npc/sell retorna erro 500 (ObjectDeletedException) exclusivamente ao vender o último item (quando a quantidade chega a zero). O erro ocorre porque a PlayerItemEntity está sendo removida, mas o JPA tenta fazer um merge nela ou na entidade pai na mesma transação. Corrija a lógica do NpcShopService para garantir que, se a nova quantidade for zero, o item seja removido definitivamente do banco sem causar conflito de estado no Hibernate. Pare e aguarde minha validação do código

Erro no insec:

main.js?attr=o-yLReK…7kAOWU3BNM0Vw6:4954
POST http://localhost:8080/api/shop/npc/sell 500 (Internal Server Error)
api.ts:98 [apiFetch] ✗ POST /api/shop/npc/sell
{status: 500, sentBody: {…}, errorBody: {…}}
errorBody
:
{error: 'Internal server error: org.hibernate.ObjectDeleted…frastructure.persistence.PlayerItemEntity#<null>]'}
sentBody
:
{playerId: 110, playerItemId: 'f1ef5414-9ee0-435a-af13-850951914bbb', quantity: 1}
status
:
500
[[Prototype]]
:
Object