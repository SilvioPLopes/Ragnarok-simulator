package com.ragnarok.api.dto.response;

import java.util.List;

public record NpcResponseDTO(
        Long id,
        String name,
        String type,
        int x,
        int y,
        String spriteRef,
        String spriteUrl,
        List<String> warpDestinations
) {}
