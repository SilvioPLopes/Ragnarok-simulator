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

    // --- FLATTENING (Stats direto na tabela) ---
    private Integer attack;
    private Integer defense;
    private Integer magicAttack;

    private Integer efeito; // O valor da cura ou buff
    private Integer bonusStr;
    private Integer bonusAgi;
    private Integer bonusVit;
    private Integer bonusInt;
    private Integer bonusDex;
    private Integer bonusLuk;

    @Column(name = "range_val") // 'range' é palavra reservada em alguns SQLs
    private Integer range;
    private Integer stats; // tentei adicionar isso para resolver o erro do isStats mas acho que não é isso
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
        statsObj.setMAttack(this.magicAttack);;
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

        return statsObj;
    }
}