package com.ragnarok.runner.populator.dto;

public record NaviNpcData(
        String mapName,
        int    npcId,
        String name,
        String spriteClass,
        int    spriteJobId,
        int    x,
        int    y
) {}
