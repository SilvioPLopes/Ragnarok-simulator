package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "npcs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NpcEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seed_id", unique = true, nullable = false)
    private String seedId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NpcType type;

    private int x;
    private int y;

    @Column(name = "map_name", nullable = false)
    private String mapName;

    @Column(name = "sprite_ref")
    private String spriteRef;

    @Column(name = "sprite_url")
    private String spriteUrl;

    @Column(columnDefinition = "TEXT")
    private String dialog;

    @OneToMany(mappedBy = "npc", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NpcShopItemEntity> shopItems = new ArrayList<>();

    @OneToMany(mappedBy = "npc", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NpcWarpDestinationEntity> warpDestinations = new ArrayList<>();
}
