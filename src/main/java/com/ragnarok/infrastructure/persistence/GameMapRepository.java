package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Repositório pertence à INFRAESTRUTURA
@Repository
public interface GameMapRepository extends JpaRepository<GameMapEntity, String> {
    // Como o ID já é o nome único (moc_fild08), não precisamos de findByUniqueName
}