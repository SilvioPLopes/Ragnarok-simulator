package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "player_skills",
    uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "skill_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "skill_id", nullable = false)
    private String skillId;

    @Column(name = "current_level", nullable = false)
    private Integer currentLevel;
}
