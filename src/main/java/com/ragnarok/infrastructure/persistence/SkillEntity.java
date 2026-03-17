package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skills")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillEntity {

    @Id
    private Long id;

    @Column(name = "aegis_name")
    private String aegisName;

    private String name;

    private String type;

    @Column(columnDefinition = "TEXT")
    private String script;
}
