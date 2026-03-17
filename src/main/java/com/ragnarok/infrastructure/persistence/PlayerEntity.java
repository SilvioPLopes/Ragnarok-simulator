package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "players")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "job_class")
    private String jobClass;

    private String gender;

    @Column(name = "base_level")
    private Integer baseLevel;

    @Column(name = "job_level")
    private Integer jobLevel;

    @Column(name = "base_exp")
    private Long baseExp;

    @Column(name = "job_exp")
    private Long jobExp;

    private Long zenny;

    @Column(name = "stat_points")
    private Integer statPoints;

    @Column(name = "skill_points")
    private Integer skillPoints;

    @Column(name = "hp_current")
    private Integer hpCurrent;

    @Column(name = "sp_current")
    private Integer spCurrent;

    // --- FLATTENING: PlayerStats (Trazendo para a tabela principal) ---
    // Assim evitamos complexidade de @Embedded agora
    private Integer str;
    private Integer agi;
    private Integer vit;
    @Column(name = "int_val")
    private Integer intelligence; // 'int' é palavra reservada em alguns bancos
    private Integer dex;
    private Integer luk;

    // Stats derivados (opcional salvar no banco, mas bom ter cache)
    private Integer hpMax;
    private Integer spMax;
    private Integer hit;
    private Integer flee;
    private Integer def;
    private Integer mDef;

    // --- FLATTENING: PlayerLocation ---
    private String mapName;
    private Integer coordX;
    private Integer coordY;


    // --- Buffs temporários (JSON serializado) ---
    @Column(name = "active_buffs", columnDefinition = "TEXT")
    private String activeBuffsJson;

    // --- Relacionamento com Inventário ---

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private java.util.List<PlayerItemEntity> inventory = new java.util.ArrayList<>();

    // Método Helper para adicionar itens garantindo a consistência dos dois lados
    public void addItem(PlayerItemEntity itemEntity) {
        this.inventory.add(itemEntity);
        itemEntity.setPlayer(this); // Vincula o item a este jogador
    }
}