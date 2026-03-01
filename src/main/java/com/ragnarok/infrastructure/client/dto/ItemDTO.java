package com.ragnarok.infrastructure.client.dto;

import java.util.List;

public record ItemDTO(
        String _id,
        Long id,
        String name,
        String description,
        String img,
        List<ItemDropRateDTO> drop_rate,
        EquipableDTO equipable
) {
    public record ItemDropRateDTO(
            String monster,
            String rate,
            String highest_spawn,
            String element,
            String flee,
            String hit
    ) {}

    // Campos comuns de equipamentos. O Jackson vai mapear como null se vier vazio {}
    public record EquipableDTO(
            Integer attack,
            Integer defense,
            Integer range,
            Integer slots,
            Integer level_min
    ) {}
}