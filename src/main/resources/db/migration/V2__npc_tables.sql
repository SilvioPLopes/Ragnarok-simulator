-- V2: NPC system tables
CREATE TABLE IF NOT EXISTS npcs (
    id         BIGSERIAL PRIMARY KEY,
    seed_id    VARCHAR(50) UNIQUE NOT NULL,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(20) NOT NULL,
    x          INTEGER NOT NULL,
    y          INTEGER NOT NULL,
    map_name   VARCHAR(100) NOT NULL,
    sprite_ref VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS npc_shop_items (
    id        BIGSERIAL PRIMARY KEY,
    npc_id    BIGINT NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    item_id   BIGINT NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    price     INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS npc_warp_destinations (
    id       BIGSERIAL PRIMARY KEY,
    npc_id   BIGINT NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    map_name VARCHAR(100) NOT NULL,
    x        INTEGER NOT NULL,
    y        INTEGER NOT NULL
);
