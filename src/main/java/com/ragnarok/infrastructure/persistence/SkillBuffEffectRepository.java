package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkillBuffEffectRepository extends JpaRepository<SkillBuffEffectEntity, Long> {
    List<SkillBuffEffectEntity> findBySkillId(Long skillId);
}
