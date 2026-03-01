package com.ragnarok.domain.model;

import lombok.*;
import java.util.List;
import java.util.ArrayList;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Item {

    private Long id; // ID do Ragnarok (1001)
    private String mongoId;
    private String name;
    private String description;
    private String imgUrl;
    private ItemType type;
    private EquipSlot equipSlot;

    // Objeto de Valor puro (sem @Embeddable)
    private ItemStats stats;

    // Lista pura (sem @OneToMany)
    private List<ItemDropInfo> droppedBy;
}