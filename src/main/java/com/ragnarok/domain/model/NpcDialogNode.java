package com.ragnarok.domain.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;
import java.util.Map;

/**
 * Nó de diálogo de NPC. Sealed interface — apenas 3 subtipos possíveis.
 * Jackson usa o campo "type" como discriminador ao serializar/desserializar.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NpcDialogNode.DialogNode.class, name = "dialog"),
    @JsonSubTypes.Type(value = NpcDialogNode.MenuNode.class,   name = "menu"),
    @JsonSubTypes.Type(value = NpcDialogNode.ActionNode.class, name = "action")
})
public sealed interface NpcDialogNode
        permits NpcDialogNode.DialogNode, NpcDialogNode.MenuNode, NpcDialogNode.ActionNode {

    /**
     * Balão de texto. "speaker" é o nome entre [colchetes] do primeiro mes(),
     * extraído e removido de texts. "next" é o índice do próximo nó.
     */
    @JsonTypeName("dialog")
    record DialogNode(
            String       speaker,
            List<String> texts,
            Integer      next
    ) implements NpcDialogNode {}

    /**
     * Menu de escolha (rAthena select/switch).
     * "texts" são linhas mes() antes do select (geralmente vazias).
     * Cada choice.next pode ser um índice numérico (como string) ou
     * "__shop__" / "__close__" (gerados pelo frontend para shops).
     */
    @JsonTypeName("menu")
    record MenuNode(
            List<String>   texts,
            List<Choice>   choices
    ) implements NpcDialogNode {

        public record Choice(String label, String next) {}
    }

    /**
     * Ação executável pelo frontend.
     * action: "close" | "heal" | "warp" | "getitem"
     * params: mapa com parâmetros dependentes da ação (pode ser null).
     */
    @JsonTypeName("action")
    record ActionNode(
            String              action,
            Map<String, Object> params
    ) implements NpcDialogNode {}
}
