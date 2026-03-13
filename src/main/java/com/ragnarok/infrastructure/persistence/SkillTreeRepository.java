package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SkillTreeRepository extends JpaRepository<SkillTreeEntity, Integer> {
    List<SkillTreeEntity> findByJobClassIgnoreCase(String jobClass);
    List<SkillTreeEntity> findByJobClassIgnoreCaseAndSkillId(String jobClass, String skillId);

    @Query("SELECT DISTINCT UPPER(s.jobClass) FROM SkillTreeEntity s")
    List<String> findDistinctJobClasses();
}
