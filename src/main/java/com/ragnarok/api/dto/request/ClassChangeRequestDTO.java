package com.ragnarok.api.dto.request;

/** Payload para POST /api/players/{id}/class-change */
public record ClassChangeRequestDTO(String targetClass) {}
