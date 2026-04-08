package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "npc_dialogs")
@Getter @Setter @NoArgsConstructor
public class NpcDialogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "npc_id", nullable = false, unique = true)
    private Long npcId;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String nodes;
}
