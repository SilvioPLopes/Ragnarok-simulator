-- V1: Initial schema
-- Uses CREATE TABLE IF NOT EXISTS for compatibility with baseline-on-migrate=true

CREATE TABLE IF NOT EXISTS monsters (
    id             BIGINT PRIMARY KEY,
    mongo_id       VARCHAR(255),
    name           VARCHAR(255),
    size           VARCHAR(50),
    race           VARCHAR(50),
    type           VARCHAR(50),
    element_power  INTEGER,
    gif_url        VARCHAR(500),
    hp             INTEGER,
    level          INTEGER,
    def            INTEGER,
    m_def          INTEGER,
    attack         INTEGER,
    magic_attack   INTEGER,
    aspd           INTEGER,
    move_speed     INTEGER,
    base_exp       INTEGER,
    job_exp        INTEGER,
    flee           INTEGER,
    hit            INTEGER,
    defense_rating INTEGER,
    crit_shield    INTEGER,
    exp_ratio      INTEGER,
    str            INTEGER,
    agi            INTEGER,
    intelligence   INTEGER,
    luk            INTEGER,
    vit            INTEGER,
    dex            INTEGER,
    element_fire    INTEGER,
    element_water   INTEGER,
    element_earth   INTEGER,
    element_wind    INTEGER,
    element_neutral INTEGER,
    element_holy    INTEGER,
    element_shadow  INTEGER,
    element_ghost   INTEGER,
    element_undead  INTEGER,
    element_poison  INTEGER
);

CREATE TABLE IF NOT EXISTS items (
    id          BIGINT PRIMARY KEY,
    mongo_id    VARCHAR(255),
    player_id   INTEGER,
    name        VARCHAR(255),
    description TEXT,
    type        VARCHAR(50),
    weight      INTEGER,
    price       INTEGER,
    img_url     VARCHAR(500),
    attack      INTEGER,
    defense     INTEGER,
    magic_attack INTEGER,
    efeito      INTEGER,
    bonus_str   INTEGER,
    bonus_agi   INTEGER,
    bonus_vit   INTEGER,
    bonus_int   INTEGER,
    bonus_dex   INTEGER,
    bonus_luk   INTEGER,
    bonus_sp    INTEGER,
    range_val   INTEGER,
    slots       INTEGER,
    level_min   INTEGER,
    equip_slot  VARCHAR(50),
    weapon_type VARCHAR(50),
    script      TEXT
);

CREATE TABLE IF NOT EXISTS maps (
    map_id  VARCHAR(100) PRIMARY KEY,
    name    VARCHAR(255),
    type    VARCHAR(50),
    img_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS players (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255),
    job_class       VARCHAR(100),
    gender          VARCHAR(20),
    base_level      INTEGER,
    job_level       INTEGER,
    base_exp        BIGINT,
    job_exp         BIGINT,
    zenny           BIGINT,
    stat_points     INTEGER,
    skill_points    INTEGER,
    hp_current      INTEGER,
    sp_current      INTEGER,
    str             INTEGER,
    agi             INTEGER,
    vit             INTEGER,
    int_val         INTEGER,
    dex             INTEGER,
    luk             INTEGER,
    hp_max          INTEGER,
    sp_max          INTEGER,
    hit             INTEGER,
    flee            INTEGER,
    def             INTEGER,
    m_def           INTEGER,
    map_name        VARCHAR(100),
    coord_x         INTEGER,
    coord_y         INTEGER,
    active_buffs    TEXT
);

CREATE TABLE IF NOT EXISTS player_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id    BIGINT NOT NULL REFERENCES players(id),
    item_id      BIGINT NOT NULL REFERENCES items(id),
    amount       INTEGER,
    refine_level INTEGER DEFAULT 0,
    is_equipped  BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS player_skills (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id     BIGINT NOT NULL,
    skill_id      VARCHAR(100) NOT NULL,
    current_level INTEGER NOT NULL,
    UNIQUE (player_id, skill_id)
);

CREATE TABLE IF NOT EXISTS map_portals (
    id       BIGSERIAL PRIMARY KEY,
    map_from VARCHAR(100),
    x_from   INTEGER,
    y_from   INTEGER,
    map_to   VARCHAR(100),
    x_to     INTEGER,
    y_to     INTEGER
);

CREATE TABLE IF NOT EXISTS map_monsters (
    id         BIGSERIAL PRIMARY KEY,
    map_id     VARCHAR(100),
    monster_id BIGINT REFERENCES monsters(id),
    amount     INTEGER
);

CREATE TABLE IF NOT EXISTS monster_drops (
    id         BIGSERIAL PRIMARY KEY,
    monster_id BIGINT REFERENCES monsters(id),
    item_id    BIGINT REFERENCES items(id),
    rate       DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS skills (
    id             BIGINT PRIMARY KEY,
    aegis_name     VARCHAR(100),
    name           VARCHAR(255),
    type           VARCHAR(50),
    script         TEXT,
    effect_type    VARCHAR(50),
    element        VARCHAR(50),
    damage_formula TEXT,
    sp_cost        INTEGER,
    duration_turns INTEGER,
    target_type    VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS skill_tree (
    id           SERIAL PRIMARY KEY,
    job_class    VARCHAR(100),
    skill_id     VARCHAR(100),
    max_level    INTEGER,
    prereq_skill VARCHAR(100),
    prereq_level INTEGER
);

CREATE TABLE IF NOT EXISTS skill_buff_effects (
    id            BIGSERIAL PRIMARY KEY,
    skill_id      BIGINT NOT NULL,
    stat_type     VARCHAR(50) NOT NULL,
    value_formula VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS weapon_size_modifiers (
    weapon_type VARCHAR(50) PRIMARY KEY,
    small_pct   INTEGER NOT NULL,
    medium_pct  INTEGER NOT NULL,
    large_pct   INTEGER NOT NULL
);
