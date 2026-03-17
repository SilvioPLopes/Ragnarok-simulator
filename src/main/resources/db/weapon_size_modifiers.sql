-- Modificadores de dano físico por tipo de arma × tamanho do monstro
-- Fonte: rAthena src/map/battle.cpp (weapon damage size modifiers)
-- Valores em %: 100 = dano normal, 75 = -25%, 50 = -50%
-- Colunas: weapon_type | small_pct | medium_pct | large_pct

CREATE TABLE IF NOT EXISTS weapon_size_modifiers (
    weapon_type  VARCHAR(50) PRIMARY KEY,
    small_pct    INTEGER NOT NULL DEFAULT 100,
    medium_pct   INTEGER NOT NULL DEFAULT 100,
    large_pct    INTEGER NOT NULL DEFAULT 100
);

INSERT INTO weapon_size_modifiers (weapon_type, small_pct, medium_pct, large_pct) VALUES
    ('NONE',               100, 100, 100),   -- Punhos nus
    ('DAGGER',             100,  75,  50),
    ('SWORD',               75, 100,  75),
    ('TWO_HAND_SWORD',      75,  75, 100),
    ('SPEAR',               75,  75, 100),
    ('TWO_HAND_SPEAR',      75,  75, 100),
    ('AXE',                 50, 100, 100),
    ('TWO_HAND_AXE',        50,  75, 100),
    ('MACE',                75, 100, 100),
    ('STAFF',              100, 100, 100),
    ('BOW',                100, 100,  50),
    ('KNUCKLE',            100,  75,  50),
    ('MUSICAL_INSTRUMENT',  75,  75,  50),
    ('WHIP',               100,  75,  50),
    ('BOOK',               100, 100,  50),
    ('KATAR',              100,  75,  50)
ON CONFLICT (weapon_type) DO UPDATE
    SET small_pct  = EXCLUDED.small_pct,
        medium_pct = EXCLUDED.medium_pct,
        large_pct  = EXCLUDED.large_pct;
