package com.ragnarok.domain.model;

public enum JobClass {
    NOVICE    (1,1,1,1,1,1, 40, 40, "Aventureiro iniciante"),
    SWORDSMAN (9,5,6,1,4,3, 60, 30, "Guerreiro corpo a corpo"),
    MAGE      (1,3,1,9,3,3, 30, 80, "Mestre das magias"),
    ARCHER    (3,7,3,1,9,3, 45, 40, "Precisão e velocidade"),
    ACOLYTE   (1,4,4,5,3,3, 50, 70, "Suporte e cura"),
    THIEF     (4,9,2,1,4,5, 40, 35, "Velocidade e evasão"),
    KNIGHT        (12,7,8,1,6,4,  80, 30, "Cavaleiro"),
    CRUSADER      (10,5,10,1,5,4, 90, 40, "Cavaleiro sagrado"),
    // Mage
    WIZARD        (1,3,1,12,4,3,  35, 100, "Mago poderoso"),
    SAGE          (1,4,2,10,6,3,  35, 90,  "Mago estudioso"),
    // Archer
    HUNTER        (3,9,3,1,12,3,  50, 40, "Caçador"),
    BARD          (3,8,3,3,10,5,  45, 50, "Bardo"),
    DANCER        (3,9,2,3,10,5,  40, 50, "Dançarina"),
    // Acolyte
    PRIEST        (1,4,5,7,4,3,   60, 90, "Sacerdote"),
    MONK          (8,6,7,3,4,3,   70, 60, "Monge"),
    // Thief
    ASSASSIN      (6,10,3,1,6,6,  45, 35, "Assassino"),
    ROGUE         (5,9,3,1,8,6,   45, 40, "Ladino"),
    // Merchant
    MERCHANT      (4,4,4,2,6,4,   55, 35, "Mercador"),
    BLACKSMITH    (8,4,6,2,6,4,   65, 35, "Ferreiro"),
    ALCHEMIST     (4,4,5,6,6,4,   55, 60, "Alquimista"),

    // Transcendentes (High)
    LORD_KNIGHT   (14,8,9,1,7,4,  90, 30, "Cavaleiro supremo"),
    PALADIN       (11,5,12,1,5,4, 100,45, "Paladino"),
    HIGH_WIZARD   (1,3,1,14,4,3,  35, 110,"Alto mago"),
    PROFESSOR     (1,4,2,12,7,3,  35, 100,"Professor"),
    SNIPER        (3,10,3,1,14,3, 55, 40, "Atirador de elite"),
    CLOWN         (3,9,3,3,12,5,  45, 55, "Bobo da corte"),
    GYPSY         (3,10,2,3,12,5, 40, 55, "Cigana"),
    HIGH_PRIEST   (1,4,5,9,4,3,   65, 100,"Sumo sacerdote"),
    CHAMPION      (9,7,8,3,4,3,   75, 65, "Campeão"),
    ASSASSIN_CROSS (7,12,3,1,7,7,  50, 35, "Assassino sombrio"),
    STALKER       (5,10,3,1,9,7,  45, 45, "Perseguidor"),
    MASTERSMITH   (9,4,7,2,7,4,   70, 35, "Mestre ferreiro"),
    CREATOR       (4,4,5,8,6,4,   55, 70, "Criador"),

    // 4ª Classe
    RUNE_KNIGHT   (16,9,10,1,8,4,  100,30, "Cavaleiro das runas"),
    ROYAL_GUARD   (12,5,14,1,5,4,  110,45, "Guarda real"),
    WARLOCK       (1,3,1,16,4,3,   35, 120,"Bruxo"),
    SORCERER      (1,4,2,14,8,3,   35, 110,"Feiticeiro"),
    RANGER        (3,11,3,1,16,3,  55, 40, "Patrulheiro"),
    MINSTREL      (3,10,3,3,14,5,  45, 60, "Menestrel"),
    WANDERER      (3,11,2,3,14,5,  40, 60, "Errante"),
    ARCHBISHOP    (1,4,5,11,4,3,   65, 110,"Arcebispo"),
    SURA          (10,8,9,3,4,3,   80, 70, "Sura"),
    GUILLOTINE_CROSS (8,13,3,1,8,8, 50, 35, "Cruz guilhotina"),
    SHADOW_CHASER (5,11,3,1,10,8,  45, 50, "Perseguidor sombrio"),
    MECHANIC      (10,4,8,2,8,4,   75, 35, "Mecânico"),
    GENETIC       (4,4,5,10,6,4,   55, 80, "Geneticista"),

    // Especiais
    SUPER_NOVICE  (3,5,3,5,5,5,   60, 60, "Super novato"),
    SUMMONER      (1,5,3,5,5,8,   45, 70, "Invocador");

    public final int str, agi, vit, intel, dex, luk;
    public final int baseHp, baseSp;
    public final String descricao;

    JobClass(int str, int agi, int vit, int intel, int dex, int luk,
             int baseHp, int baseSp, String descricao) {
        this.str     = str;
        this.agi     = agi;
        this.vit     = vit;
        this.intel   = intel;
        this.dex     = dex;
        this.luk     = luk;
        this.baseHp  = baseHp;
        this.baseSp  = baseSp;
        this.descricao = descricao;
    }

}
