package com.ragnarok.infrastructure.persistence;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkillBuffEffectRepository extends JpaRepository<SkillBuffEffectEntity, Long> {
    @Cacheable("skillBuffEffects")
    List<SkillBuffEffectEntity> findBySkillId(Long skillId);
}
