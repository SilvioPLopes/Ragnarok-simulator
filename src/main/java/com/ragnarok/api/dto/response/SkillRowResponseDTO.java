package com.ragnarok.api.dto.response;

public record SkillRowResponseDTO(
        String aegisName, String name,
        Integer maxLevel, Integer currentLevel,
        Boolean canLearn, String blockedReason,
        Boolean targetable
) {}
