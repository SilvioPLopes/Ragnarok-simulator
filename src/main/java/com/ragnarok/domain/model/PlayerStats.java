package com.ragnarok.domain.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor
public class PlayerStats {
    private Integer str;
    private Integer agi;
    private Integer vit;
    private Integer intVal;
    private Integer dex;
    private Integer luk;

    private Integer maxHp;
    private Integer maxSp;

    // Construtor com todos os campos — usado em PlayerService.criarNovoPersonagem
    public PlayerStats(Integer str, Integer agi, Integer vit, Integer intVal,
                       Integer dex, Integer luk, Integer maxHp, Integer maxSp) {
        this.str = str;
        this.agi = agi;
        this.vit = vit;
        this.intVal = intVal;
        this.dex = dex;
        this.luk = luk;
        this.maxHp = maxHp;
        this.maxSp = maxSp;
    }

}

