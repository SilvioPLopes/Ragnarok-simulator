package com.ragnarok.api.dto.response;

public record WalkResponseDTO(
        boolean encounterOccurred,
        Long monsterId,
        String monsterName,
        Integer monsterHp,
        String message
) {}
