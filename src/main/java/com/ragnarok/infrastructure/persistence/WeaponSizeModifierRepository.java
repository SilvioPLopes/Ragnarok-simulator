package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WeaponSizeModifierRepository extends JpaRepository<WeaponSizeModifierEntity, String> {
    Optional<WeaponSizeModifierEntity> findByWeaponType(String weaponType);
}
