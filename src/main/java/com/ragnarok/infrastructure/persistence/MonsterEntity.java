package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "monsters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MonsterEntity {

    @Id
    private Long id;

    @Column(name = "mongo_id")
    private String mongoId;
    private String name;
    private String size;
    private String race;
    private String type;

    @Column(name = "element_power")
    private Integer elementPower;

    @Column(name = "gif_url")
    private String gifUrl;


    private Integer hp;
    private Integer level;
    private Integer def;
    private Integer mDef;
    private Integer attack;
    private Integer magicAttack;
    private Integer aspd;
    private Integer moveSpeed;
    private Integer baseExp;
    private Integer jobExp;
    private Integer flee;
    private Integer hit;
    private Integer defenseRating;
    private Integer critShield;
    private Integer expRatio;

    private Integer agi;
    private Integer intelligence;
    private Integer luk;
    private Integer vit;
    private Integer dex;

    private Integer elementFire;
    private Integer elementWater;
    private Integer elementEarth;
    private Integer elementWind;
    private Integer elementNeutral;
    private Integer elementHoly;
    private Integer elementShadow;
    private Integer elementGhost;
    private Integer elementUndead;
    private Integer elementPoison;

    @OneToMany(mappedBy = "monster", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MonsterDropEntity> drops = new ArrayList<>();

    public void addDrop(ItemEntity item, Double rate) {
        MonsterDropEntity drop = new MonsterDropEntity();
        drop.setMonster(this);
        drop.setItem(item);
        drop.setRate(rate);
        this.drops.add(drop);
    }

    @OneToMany(mappedBy = "monster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<MonsterSpawnEntity> spawns = new java.util.ArrayList<>();

    // Método helper
    public void addSpawn(GameMapEntity map, Integer amount, String respawnTime) {
        MonsterSpawnEntity spawn = new MonsterSpawnEntity();
        spawn.setMonster(this);
        spawn.setMap(map);
        spawn.setAmount(amount);
        spawn.setRespawnTime(respawnTime);
        this.spawns.add(spawn);
    }
}