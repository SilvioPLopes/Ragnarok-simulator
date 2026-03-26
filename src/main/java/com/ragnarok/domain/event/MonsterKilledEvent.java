package com.ragnarok.domain.event;

import com.ragnarok.domain.model.Item;
import java.util.List;

public record MonsterKilledEvent(
        Long playerId,
        Long monsterId,
        List<Item> loot,
        long baseExp,
        long jobExp
) {}
