package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "npc_dialogs")
@Getter @Setter @NoArgsConstructor
public class NpcDialogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "npc_id", nullable = false, unique = true)
    private Long npcId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String nodes;
}
