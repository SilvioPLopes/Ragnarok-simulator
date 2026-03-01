package com.ragnarok.domain.model;

public enum ItemType {
    WEAPON,     // Armas (Tem Atk, Nível, Slots)
    ARMOR,      // Armaduras/Roupas (Tem Def, Slots)
    CONSUMABLE, // Poções, Comidas (Tem efeito, some ao usar)
    ETC,        // Loot geral (Jellopy, etc)
    AMMO,       // Flechas
    CARD        // Cartas
}