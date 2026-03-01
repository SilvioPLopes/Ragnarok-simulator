package com.ragnarok.domain.model;

import lombok.*;

// Sem @Embeddable, sem jakarta.persistence. Apenas dados.
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerStats {
    private Integer str;
    private Integer agi;
    private Integer vit;
    private Integer intVal; // Atenção: no Entity vamos chamar de 'intelligence' para evitar erro SQL
    private Integer dex;
    private Integer luk;
    private Integer Hp;
    private Integer Sp;
    private Integer MaxHp;
    private Integer MaxSp;

    private Integer statusPoints;
    private Integer skillPoints;

}