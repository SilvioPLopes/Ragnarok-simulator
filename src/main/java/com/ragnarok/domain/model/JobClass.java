package com.ragnarok.domain.model;

public enum JobClass {
    NOVICE    (1,1,1,1,1,1, 40, 40, "Aventureiro iniciante"),
    SWORDSMAN (9,5,6,1,4,3, 60, 30, "Guerreiro corpo a corpo"),
    MAGE      (1,3,1,9,3,3, 30, 80, "Mestre das magias"),
    ARCHER    (3,7,3,1,9,3, 45, 40, "Precisão e velocidade"),
    ACOLYTE   (1,4,4,5,3,3, 50, 70, "Suporte e cura"),
    THIEF     (4,9,2,1,4,5, 40, 35, "Velocidade e evasão");

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
