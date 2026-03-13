package com.ragnarok.infrastructure.persistence;

import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.ItemStats;
import com.ragnarok.domain.model.ItemType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ItemEntity {

    @Id
    private Long id; // ID manual (1001)

    @Column(name = "mongo_id")
    private String mongoId;
    private Integer playerId;
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ItemType type;

    private Integer weight;
    private Integer price;

    @Column(name = "img_url")
    private String imgUrl;
    private Integer attack;
    private Integer defense;
    private Integer magicAttack;

    private Integer efeito;
    private Integer bonusStr;
    private Integer bonusAgi;
    private Integer bonusVit;
    private Integer bonusInt;
    private Integer bonusDex;
    private Integer bonusLuk;

    @Column(name = "bonus_sp")
    private Integer bonusSp;

    @Column(name = "range_val")
    private Integer range;
    private Integer slots;

    @Column(name = "level_min")
    private Integer levelMin;

    @Enumerated(EnumType.STRING)
    private EquipSlot equipSlot;

    public Integer getAttack() {
        return attack != null ? attack : 0;
    }

    public Integer getDefense() {
        return defense != null ? defense : 0;
    }

    public ItemStats getStats() {
        ItemStats statsObj = new ItemStats();

        statsObj.setAttack(this.attack);
        statsObj.setDefense(this.defense);
        statsObj.setMAttack(this.magicAttack);
        statsObj.setRange(this.range);
        statsObj.setSlots(this.slots);
        statsObj.setLevelMin(this.levelMin);

        // Mapeia os novos campos
        statsObj.setEfeito(this.efeito);
        statsObj.setBonusStr(this.bonusStr);
        statsObj.setBonusAgi(this.bonusAgi);
        statsObj.setBonusVit(this.bonusVit);
        statsObj.setBonusInt(this.bonusInt);
        statsObj.setBonusDex(this.bonusDex);
        statsObj.setBonusLuk(this.bonusLuk);
        statsObj.setBonusSP(this.bonusSp);

        return statsObj;
    }
}