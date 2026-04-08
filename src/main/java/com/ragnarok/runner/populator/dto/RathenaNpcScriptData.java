package com.ragnarok.runner.populator.dto;

import com.ragnarok.domain.model.NpcDialogNode;
import java.util.List;

public record RathenaNpcScriptData(
        String mapName,
        int    x,
        int    y,
        String name,
        int    spriteId,
        String dialog,           // flat text legado (mes() concatenados)
        List<NpcDialogNode> nodes // árvore estruturada (nova)
) {}
