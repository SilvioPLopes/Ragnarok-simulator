package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SkillTreeRepository extends JpaRepository<SkillTreeEntity, Integer> {
    List<SkillTreeEntity> findByJobClassIgnoreCase(String jobClass);
    List<SkillTreeEntity> findByJobClassIgnoreCaseAndSkillId(String jobClass, String skillId);

    @Query("SELECT s FROM SkillTreeEntity s WHERE UPPER(s.jobClass) IN :upperJobClasses")
    List<SkillTreeEntity> findByJobClassesIn(@Param("upperJobClasses") List<String> upperJobClasses);

    @Query("SELECT s FROM SkillTreeEntity s WHERE UPPER(s.jobClass) IN :upperJobClasses AND UPPER(s.skillId) = UPPER(:skillId)")
    List<SkillTreeEntity> findByJobClassesInAndSkillId(@Param("upperJobClasses") List<String> upperJobClasses, @Param("skillId") String skillId);

    @Query("SELECT DISTINCT UPPER(s.jobClass) FROM SkillTreeEntity s")
    List<String> findDistinctJobClasses();
}
