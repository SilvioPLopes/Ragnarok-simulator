package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkillTreeRepository extends JpaRepository<SkillTreeEntity, Integer> {
    List<SkillTreeEntity> findByJobClassIgnoreCase(String jobClass);
}
