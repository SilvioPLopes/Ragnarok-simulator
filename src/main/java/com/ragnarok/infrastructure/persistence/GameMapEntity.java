package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "maps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GameMapEntity { // Nome da classe: GameMapEntity

    @Id
    @Column(name = "map_id")
    private String id; // ID é String (ex: "moc_fild08")

    private String name;
    private String type;
    private String imgUrl;
}