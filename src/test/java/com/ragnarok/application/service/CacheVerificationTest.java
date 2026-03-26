package com.ragnarok.application.service;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierEntity;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies that Spring Cache is active and prevents repeated database queries
 * for static game data.
 *
 * Uses @SpyBean to count actual repository invocations.
 * Seeds the database in @BeforeEach for deterministic call-count assertions.
 */
class CacheVerificationTest extends AbstractIntegrationTest {

    @Autowired
    WeaponSizeService weaponSizeService;

    @SpyBean
    WeaponSizeModifierRepository weaponSizeModifierRepository;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear cache before each test to ensure clean state
        cacheManager.getCache("weaponSizeModifiers").clear();

        // Seed the table so findByWeaponType is actually called (not short-circuited to default).
        // WeaponSizeModifierEntity uses @AllArgsConstructor: (weaponType, smallPct, mediumPct, largePct)
        if (weaponSizeModifierRepository.count() == 0) {
            weaponSizeModifierRepository.save(
                    new WeaponSizeModifierEntity("SWORD", 75, 100, 75));
        }
    }

    @Test
    @DisplayName("Cache: getModifier() should query the database only on the first call")
    void getModifier_shouldHitDatabaseOnlyOnce() {
        // First call — cache miss, hits the database
        weaponSizeService.getModifier(WeaponType.SWORD, "Medium");
        // Second call with same args — cache hit, no database call
        weaponSizeService.getModifier(WeaponType.SWORD, "Medium");

        // Repository should have been called exactly once (only the first call)
        verify(weaponSizeModifierRepository, times(1)).findByWeaponType("SWORD");
    }

    @Test
    @DisplayName("Cache: cache manager should have all expected caches configured")
    void cacheManager_shouldHaveExpectedCaches() {
        assertNotNull(cacheManager.getCache("weaponSizeModifiers"));
        assertNotNull(cacheManager.getCache("skillBuffEffects"));
        assertNotNull(cacheManager.getCache("skillTree"));
        assertNotNull(cacheManager.getCache("playerSkills"));
    }
}
