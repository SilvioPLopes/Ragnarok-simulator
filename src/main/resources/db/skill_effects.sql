-- Altera tabela skills com novos campos de efeito
ALTER TABLE skills ADD COLUMN IF NOT EXISTS effect_type    VARCHAR(50);
ALTER TABLE skills ADD COLUMN IF NOT EXISTS element        VARCHAR(50);
ALTER TABLE skills ADD COLUMN IF NOT EXISTS damage_formula TEXT;
ALTER TABLE skills ADD COLUMN IF NOT EXISTS sp_cost        INTEGER;
ALTER TABLE skills ADD COLUMN IF NOT EXISTS duration_turns INTEGER;
ALTER TABLE skills ADD COLUMN IF NOT EXISTS target_type    VARCHAR(20) DEFAULT 'SINGLE';

-- Cria tabela de efeitos de buff por skill
CREATE TABLE IF NOT EXISTS skill_buff_effects (
    id           SERIAL PRIMARY KEY,
    skill_id     BIGINT NOT NULL REFERENCES skills(id),
    stat_type    VARCHAR(30) NOT NULL,
    value_formula TEXT NOT NULL
);

-- ---------------------------------------------------------------
-- Popula effect_type, element, damage_formula, sp_cost para skills principais
-- ---------------------------------------------------------------

-- Passivas (masteries e recuperação)
UPDATE skills SET effect_type = 'PASSIVE', sp_cost = 0 WHERE aegis_name IN (
    'SM_SWORD', 'SM_TWOHAND', 'SM_RECOVERY', 'NV_BASIC',
    'MG_SRECOVERY', 'AC_OWL', 'AC_VULTURE', 'PR_MACEMASTERY',
    'MC_INCCARRY', 'AL_DP', 'AL_DEMONBANE'
);

-- Bash — dano físico simples
UPDATE skills SET effect_type = 'PHYSICAL_DAMAGE', element = 'NEUTRAL',
    damage_formula = 'ATK * skill_lv * 1.3',
    sp_cost = 8, target_type = 'SINGLE'
WHERE aegis_name = 'SM_BASH';

-- Magnum Break — dano físico em área + buff de fogo
UPDATE skills SET effect_type = 'PHYSICAL_DAMAGE', element = 'FIRE',
    damage_formula = 'ATK * 1.2 + skill_lv * 20',
    sp_cost = 30, target_type = 'SINGLE'
WHERE aegis_name = 'SM_MAGNUM';

-- Endure — buff de resistência
UPDATE skills SET effect_type = 'BUFF', sp_cost = 10, duration_turns = 7,
    target_type = 'SELF'
WHERE aegis_name = 'SM_ENDURE';

-- Provoke — debuff no monstro (muda tipo para STATUS_EFFECT por enquanto)
UPDATE skills SET effect_type = 'STATUS_EFFECT', sp_cost = 8, target_type = 'SINGLE'
WHERE aegis_name = 'SM_PROVOKE';

-- Soul Strike — dano espectral
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'GHOST',
    damage_formula = 'MATK * skill_lv * 0.75',
    sp_cost = 12, target_type = 'SINGLE'
WHERE aegis_name = 'MG_SOULSTRIKE';

-- Cold Bolt — dano de água
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'WATER',
    damage_formula = 'MATK * skill_lv * 0.8',
    sp_cost = 12, target_type = 'SINGLE'
WHERE aegis_name = 'MG_COLDBOLT';

-- Fire Bolt — dano de fogo
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'FIRE',
    damage_formula = 'MATK * skill_lv * 0.8',
    sp_cost = 12, target_type = 'SINGLE'
WHERE aegis_name = 'MG_FIREBOLT';

-- Lightning Bolt — dano de vento
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'WIND',
    damage_formula = 'MATK * skill_lv * 0.8',
    sp_cost = 12, target_type = 'SINGLE'
WHERE aegis_name = 'MG_LIGHTNINGBOLT';

-- Fire Ball — dano de fogo (mais forte que fire bolt)
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'FIRE',
    damage_formula = 'MATK * skill_lv * 1.2 + INT * skill_lv',
    sp_cost = 20, target_type = 'SINGLE'
WHERE aegis_name = 'MG_FIREBALL';

-- Thunderstorm — dano de vento em área
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'WIND',
    damage_formula = 'MATK * skill_lv * 0.6',
    sp_cost = 29, target_type = 'AOE'
WHERE aegis_name = 'MG_THUNDERSTORM';

-- Napalm Beat — dano neutro
UPDATE skills SET effect_type = 'MAGICAL_DAMAGE', element = 'NEUTRAL',
    damage_formula = 'MATK * skill_lv * 0.7',
    sp_cost = 9, target_type = 'SINGLE'
WHERE aegis_name = 'MG_NAPALMBEAT';

-- Heal — cura baseada em INT e nível
UPDATE skills SET effect_type = 'HEAL',
    damage_formula = 'INT * skill_lv * 4 + MaxHP * skill_lv / 40',
    sp_cost = 10, target_type = 'SELF'
WHERE aegis_name = 'AL_HEAL';

-- Blessing — buff de STR/INT/DEX
UPDATE skills SET effect_type = 'BUFF', sp_cost = 28,
    duration_turns = 10, target_type = 'SELF'
WHERE aegis_name = 'AL_BLESSING';

-- Increase AGI — buff de AGI e FLEE
UPDATE skills SET effect_type = 'BUFF', sp_cost = 18,
    duration_turns = 10, target_type = 'SELF'
WHERE aegis_name = 'AL_INCAGI';

-- Decrease AGI — debuff de AGI (usa STATUS_EFFECT por ora)
UPDATE skills SET effect_type = 'STATUS_EFFECT', sp_cost = 15, target_type = 'SINGLE'
WHERE aegis_name = 'AL_DECAGI';

-- Angelus — buff de MaxHP%
UPDATE skills SET effect_type = 'BUFF', sp_cost = 23,
    duration_turns = 10, target_type = 'SELF'
WHERE aegis_name = 'AL_ANGELUS';

-- Improve Concentration (Archer) — buff de AGI/DEX
UPDATE skills SET effect_type = 'BUFF', sp_cost = 5,
    duration_turns = 8, target_type = 'SELF'
WHERE aegis_name = 'AC_CONCENTRATION';

-- Double Strafe — dano físico com arco
UPDATE skills SET effect_type = 'PHYSICAL_DAMAGE', element = 'NEUTRAL',
    damage_formula = 'ATK * skill_lv * 1.8',
    sp_cost = 12, target_type = 'SINGLE'
WHERE aegis_name = 'AC_DOUBLE';

-- ---------------------------------------------------------------
-- skill_buff_effects: detalhes dos buffs multi-stat
-- ---------------------------------------------------------------

-- Blessing (id=34): +STR, +INT, +DEX
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'STR', 'skill_lv * 2' FROM skills WHERE aegis_name = 'AL_BLESSING'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'INT', 'skill_lv * 2' FROM skills WHERE aegis_name = 'AL_BLESSING'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'DEX', 'skill_lv * 2' FROM skills WHERE aegis_name = 'AL_BLESSING'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Increase AGI (id=29): +AGI, +FLEE
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'AGI', 'skill_lv * 3' FROM skills WHERE aegis_name = 'AL_INCAGI'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'FLEE', 'skill_lv * 5' FROM skills WHERE aegis_name = 'AL_INCAGI'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Angelus (id=33): +MaxHP%
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'MAX_HP_PERCENT', 'skill_lv * 5' FROM skills WHERE aegis_name = 'AL_ANGELUS'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Endure (id=8): +DEF, +M_DEF
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'DEF', 'skill_lv * 2' FROM skills WHERE aegis_name = 'SM_ENDURE'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'M_DEF', 'skill_lv * 4' FROM skills WHERE aegis_name = 'SM_ENDURE'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Improve Concentration: +AGI, +DEX
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'AGI', 'skill_lv * 2' FROM skills WHERE aegis_name = 'AC_CONCENTRATION'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'DEX', 'skill_lv * 2' FROM skills WHERE aegis_name = 'AC_CONCENTRATION'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- ---------------------------------------------------------------
-- skill_buff_effects: passivas (turnosRestantes = -1 = permanente)
-- ---------------------------------------------------------------

-- Sword Mastery (SM_SWORD): +ATK flat por nível
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'ATK', 'skill_lv * 4' FROM skills WHERE aegis_name = 'SM_SWORD'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Two-Hand Sword Mastery (SM_TWOHAND): +ATK flat por nível
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'ATK', 'skill_lv * 5' FROM skills WHERE aegis_name = 'SM_TWOHAND'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Mace Mastery (PR_MACEMASTERY): +ATK flat por nível
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'ATK', 'skill_lv * 3' FROM skills WHERE aegis_name = 'PR_MACEMASTERY'
ON CONFLICT (skill_id, stat_type) DO NOTHING;

-- Owl's Eye (AC_OWL): +DEX por nível
INSERT INTO skill_buff_effects (skill_id, stat_type, value_formula)
SELECT id, 'DEX', 'skill_lv * 1' FROM skills WHERE aegis_name = 'AC_OWL'
ON CONFLICT (skill_id, stat_type) DO NOTHING;
