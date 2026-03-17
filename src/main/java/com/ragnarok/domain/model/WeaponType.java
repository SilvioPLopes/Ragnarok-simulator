package com.ragnarok.domain.model;

/**
 * Sub-tipo de arma. Usado para calcular modificadores de tamanho (Small/Medium/Large).
 * Mapeado do campo SubType do rAthena item_db.yml.
 */
public enum WeaponType {
    NONE,                // Punhos nus
    DAGGER,              // Adagas
    SWORD,               // Espada 1H
    TWO_HAND_SWORD,      // Espada 2H
    SPEAR,               // Lança 1H
    TWO_HAND_SPEAR,      // Lança 2H
    AXE,                 // Machado 1H
    TWO_HAND_AXE,        // Machado 2H
    MACE,                // Maça
    STAFF,               // Cajado (1H e 2H)
    BOW,                 // Arco
    KNUCKLE,             // Soco-inglês
    MUSICAL_INSTRUMENT,  // Instrumento (Bard)
    WHIP,                // Chicote (Dancer)
    BOOK,                // Livro (Scholar)
    KATAR                // Katar (Assassin)
}
