package com.ragnarok.application.service;

import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierEntity;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaponSizeServiceTest {

    @Mock
    private WeaponSizeModifierRepository repository;

    @InjectMocks
    private WeaponSizeService service;

    /** Creates a modifier entity. Constructor order: (weaponType, smallPct, mediumPct, largePct). */
    private WeaponSizeModifierEntity modifier(int small, int medium, int large) {
        return new WeaponSizeModifierEntity("SWORD", small, medium, large);
    }

    @Test
    @DisplayName("monsterSize=Small retorna smallPct")
    void getModifier_small_returnsSmallPct() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, "Small");

        assertEquals(75, result);
    }

    @Test
    @DisplayName("monsterSize=Large retorna largePct")
    void getModifier_large_returnsLargePct() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, "Large");

        assertEquals(125, result);
    }

    @Test
    @DisplayName("monsterSize=Medium retorna mediumPct")
    void getModifier_medium_returnsMediumPct() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, "Medium");

        assertEquals(100, result);
    }

    @Test
    @DisplayName("monsterSize=null normaliza para MEDIUM")
    void getModifier_nullSize_defaultsMedium() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, null);

        assertEquals(100, result);
    }

    @Test
    @DisplayName("weaponType=null usa chave NONE")
    void getModifier_nullWeaponType_usesNoneKey() {
        when(repository.findByWeaponType("NONE")).thenReturn(Optional.of(modifier(80, 100, 120)));

        int result = service.getModifier(null, "Small");

        assertEquals(80, result);
    }

    @Test
    @DisplayName("repositório vazio retorna modificador padrão 100")
    void getModifier_repositoryEmpty_returnsDefault100() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.empty());

        int result = service.getModifier(WeaponType.SWORD, "Small");

        assertEquals(100, result);
    }
}
