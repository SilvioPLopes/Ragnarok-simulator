package com.ragnarok.domain.event;

public record PlayerLeveledUpEvent(
        Long playerId,
        int newBaseLevel,
        int newJobLevel
) {}
