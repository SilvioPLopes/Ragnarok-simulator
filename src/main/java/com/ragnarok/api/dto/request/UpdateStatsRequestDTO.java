package com.ragnarok.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload para PUT /api/players/{id}/stats.
 * Envia apenas os stats que deseja aumentar.
 * A chave "int" é usada no JSON pois "int" é palavra reservada em Java.
 */
public record UpdateStatsRequestDTO(
        Integer str,
        Integer agi,
        Integer vit,
        @JsonProperty("int") Integer intel,
        Integer dex,
        Integer luk
) {}
