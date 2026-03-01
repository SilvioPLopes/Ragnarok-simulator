package com.ragnarok.domain.model;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerItem {

    private UUID id;

    // Agora contém o objeto completo (com ATK, DEF, Nome)
    private Item itemDefinition;

    private Integer amount;
    private Integer refineLevel;

    // Slots simplificados
    private Integer cardSlot1;
    private Integer cardSlot2;
    private Integer cardSlot3;
    private Integer cardSlot4;

    private Boolean isEquipped;

    public String getName() {
        return itemDefinition != null ? itemDefinition.getName() : "Unknown";
    }
}