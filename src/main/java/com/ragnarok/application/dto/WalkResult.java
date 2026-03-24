package com.ragnarok.application.dto;

public record WalkResult(
        boolean encounterOccurred,
        Long monsterId,
        String monsterName,
        Integer monsterHp,
        String message
) {}
