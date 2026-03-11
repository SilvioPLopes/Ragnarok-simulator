package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "map_portals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MapPortalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_from")
    private String mapFrom;

    @Column(name = "x_from")
    private Integer xFrom;

    @Column(name = "y_from")
    private Integer yFrom;

    @Column(name = "map_to")
    private String mapTo;

    @Column(name = "x_to")
    private Integer xTo;

    @Column(name = "y_to")
    private Integer yTo;
}