package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skill_tree")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillTreeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "job_class")
    private String jobClass;

    @Column(name = "skill_id")
    private String skillId;

    @Column(name = "max_level")
    private Integer maxLevel;

    @Column(name = "prereq_skill")
    private String prereqSkill;

    @Column(name = "prereq_level")
    private Integer prereqLevel;
}
