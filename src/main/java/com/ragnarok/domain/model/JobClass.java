package com.ragnarok.domain.model;

public enum JobClass {

    // ── Tier 0 ────────────────────────────────────────────────────────────────
    NOVICE       (1,1,1,1,1,1,  40, 40, "Aventureiro iniciante",  0, null),
    SUPER_NOVICE (3,5,3,5,5,5,  60, 60, "Super novato",           0, null),
    SUMMONER     (1,5,3,5,5,8,  45, 70, "Invocador (Doram)",      0, null),

    // ── Tier 1 ────────────────────────────────────────────────────────────────
    SWORDSMAN    (9,5,6,1,4,3,  60, 30, "Guerreiro corpo a corpo",1, null),
    MAGE         (1,3,1,9,3,3,  30, 80, "Mestre das magias",      1, null),
    ARCHER       (3,7,3,1,9,3,  45, 40, "Precisão e velocidade",  1, null),
    ACOLYTE      (1,4,4,5,3,3,  50, 70, "Suporte e cura",         1, null),
    THIEF        (4,9,2,1,4,5,  40, 35, "Velocidade e evasão",    1, null),
    MERCHANT     (4,4,4,2,6,4,  55, 35, "Mercador",               1, null),

    // ── Tier 2 ────────────────────────────────────────────────────────────────
    KNIGHT       (12,7,8,1,6,4,  80, 30, "Cavaleiro",             2, SWORDSMAN),
    CRUSADER     (10,5,10,1,5,4, 90, 40, "Cavaleiro sagrado",     2, SWORDSMAN),
    WIZARD       (1,3,1,12,4,3,  35,100, "Mago poderoso",         2, MAGE),
    SAGE         (1,4,2,10,6,3,  35, 90, "Mago estudioso",        2, MAGE),
    HUNTER       (3,9,3,1,12,3,  50, 40, "Caçador",               2, ARCHER),
    BARD         (3,8,3,3,10,5,  45, 50, "Bardo",                 2, ARCHER),
    DANCER       (3,9,2,3,10,5,  40, 50, "Dançarina",             2, ARCHER),
    PRIEST       (1,4,5,7,4,3,   60, 90, "Sacerdote",             2, ACOLYTE),
    MONK         (8,6,7,3,4,3,   70, 60, "Monge",                 2, ACOLYTE),
    ASSASSIN     (6,10,3,1,6,6,  45, 35, "Assassino",             2, THIEF),
    ROGUE        (5,9,3,1,8,6,   45, 40, "Ladino",                2, THIEF),
    BLACKSMITH   (8,4,6,2,6,4,   65, 35, "Ferreiro",              2, MERCHANT),
    ALCHEMIST    (4,4,5,6,6,4,   55, 60, "Alquimista",            2, MERCHANT),

    // ── Tier 3 (Transcendentes) ───────────────────────────────────────────────
    LORD_KNIGHT    (14,8,9,1,7,4,   90, 30, "Cavaleiro supremo",  3, KNIGHT),
    PALADIN        (11,5,12,1,5,4, 100, 45, "Paladino",           3, CRUSADER),
    HIGH_WIZARD    (1,3,1,14,4,3,   35,110, "Alto mago",          3, WIZARD),
    PROFESSOR      (1,4,2,12,7,3,   35,100, "Professor",          3, SAGE),
    SNIPER         (3,10,3,1,14,3,  55, 40, "Atirador de elite",  3, HUNTER),
    CLOWN          (3,9,3,3,12,5,   45, 55, "Bobo da corte",      3, BARD),
    GYPSY          (3,10,2,3,12,5,  40, 55, "Cigana",             3, DANCER),
    HIGH_PRIEST    (1,4,5,9,4,3,    65,100, "Sumo sacerdote",     3, PRIEST),
    CHAMPION       (9,7,8,3,4,3,    75, 65, "Campeão",            3, MONK),
    ASSASSIN_CROSS (7,12,3,1,7,7,   50, 35, "Assassino sombrio",  3, ASSASSIN),
    STALKER        (5,10,3,1,9,7,   45, 45, "Perseguidor",        3, ROGUE),
    MASTERSMITH    (9,4,7,2,7,4,    70, 35, "Mestre ferreiro",    3, BLACKSMITH),
    CREATOR        (4,4,5,8,6,4,    55, 70, "Criador",            3, ALCHEMIST),

    // ── Tier 4 ────────────────────────────────────────────────────────────────
    RUNE_KNIGHT      (16,9,10,1,8,4,  100, 30, "Cavaleiro das runas",  4, LORD_KNIGHT),
    ROYAL_GUARD      (12,5,14,1,5,4,  110, 45, "Guarda real",          4, PALADIN),
    WARLOCK          (1,3,1,16,4,3,    35,120, "Bruxo",                4, HIGH_WIZARD),
    SORCERER         (1,4,2,14,8,3,    35,110, "Feiticeiro",           4, PROFESSOR),
    RANGER           (3,11,3,1,16,3,   55, 40, "Patrulheiro",          4, SNIPER),
    MINSTREL         (3,10,3,3,14,5,   45, 60, "Menestrel",            4, CLOWN),
    WANDERER         (3,11,2,3,14,5,   40, 60, "Errante",              4, GYPSY),
    ARCHBISHOP       (1,4,5,11,4,3,    65,110, "Arcebispo",            4, HIGH_PRIEST),
    SURA             (10,8,9,3,4,3,    80, 70, "Sura",                 4, CHAMPION),
    GUILLOTINE_CROSS (8,13,3,1,8,8,    50, 35, "Cruz guilhotina",      4, ASSASSIN_CROSS),
    SHADOW_CHASER    (5,11,3,1,10,8,   45, 50, "Perseguidor sombrio",  4, STALKER),
    MECHANIC         (10,4,8,2,8,4,    75, 35, "Mecânico",             4, MASTERSMITH),
    GENETIC          (4,4,5,10,6,4,    55, 80, "Geneticista",          4, CREATOR);

    // ── Campos ────────────────────────────────────────────────────────────────
    public final int str, agi, vit, intel, dex, luk;
    public final int baseHp, baseSp;
    public final String descricao;
    public final int tier;
    public final JobClass parentClass;

    JobClass(int str, int agi, int vit, int intel, int dex, int luk,
             int baseHp, int baseSp, String descricao,
             int tier, JobClass parentClass) {
        this.str         = str;
        this.agi         = agi;
        this.vit         = vit;
        this.intel       = intel;
        this.dex         = dex;
        this.luk         = luk;
        this.baseHp      = baseHp;
        this.baseSp      = baseSp;
        this.descricao   = descricao;
        this.tier        = tier;
        this.parentClass = parentClass;
    }

    /** Retorna o job level máximo desta classe */
    public int maxJobLevel() {
        return tier == 0 ? 9 : 50;
    }

    /** Retorna todas as classes que evoluem diretamente desta */
    public JobClass[] nextClasses() {
        return java.util.Arrays.stream(values())
                .filter(j -> j.parentClass == this)
                .toArray(JobClass[]::new);
    }
}