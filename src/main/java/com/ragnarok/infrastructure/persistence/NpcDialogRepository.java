package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface NpcDialogRepository extends JpaRepository<NpcDialogEntity, Long> {
    Optional<NpcDialogEntity> findByNpcId(Long npcId);

    @Modifying
    @Transactional
    void deleteByNpcId(Long npcId);
}
