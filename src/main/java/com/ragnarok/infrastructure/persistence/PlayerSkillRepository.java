package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerSkillRepository extends JpaRepository<PlayerSkillEntity, UUID> {
    List<PlayerSkillEntity> findByPlayerId(Long playerId);
    Optional<PlayerSkillEntity> findByPlayerIdAndSkillId(Long playerId, String skillId);

    void deleteByPlayerId(Long playerId);
}
