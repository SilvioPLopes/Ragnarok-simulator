package com.ragnarok.api.dto.response;

public record PlayerResponseDTO(
        Long id, String name, String jobClass,
        Integer baseLevel, Integer jobLevel,
        Integer hpCurrent, Integer hpMax,
        Integer spCurrent, Integer spMax,
        Integer str, Integer agi, Integer vit,
        Integer intelligence, Integer dex, Integer luk,
        Integer statPoints, Integer skillPoints,
        Long zenny, String mapName
) {}
