package com.ragnarok.application.dto;

public record SkillRowDTO(
        String aegisName,
        String name,
        int maxLevel,
        int currentLevel,
        boolean canLearn,
        String blockedReason   // null when canLearn = true
) {}
