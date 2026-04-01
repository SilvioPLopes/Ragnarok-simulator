package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "npc_warp_destinations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NpcWarpDestinationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private NpcEntity npc;

    @Column(name = "map_name", nullable = false)
    private String mapName;

    private int x;
    private int y;
}
