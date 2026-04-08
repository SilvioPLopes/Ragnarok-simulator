package com.ragnarok.runner.populator.dto;

public record RathenaNpcScriptData(
        String mapName,
        int    x,
        int    y,
        String name,
        int    spriteId,
        String dialog
) {}
