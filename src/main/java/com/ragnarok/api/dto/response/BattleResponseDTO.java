package com.ragnarok.api.dto.response;

public record BattleResponseDTO(
        String message,
        Integer monsterHpRemaining,
        boolean victory,
        boolean playerDied,
        MapInfoResponseDTO newMap
) {}
