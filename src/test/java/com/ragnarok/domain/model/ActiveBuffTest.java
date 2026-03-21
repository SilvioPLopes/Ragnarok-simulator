package com.ragnarok.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActiveBuffTest {

    @Test
    @DisplayName("isExpired retorna true quando turnosRestantes == 0")
    void isExpired_zero_returnsTrue() {
        ActiveBuff buff = new ActiveBuff("NV_BASIC", StatType.STR, 50, 0);

        assertTrue(buff.isExpired());
    }

    @Test
    @DisplayName("isExpired retorna false quando turnosRestantes > 0")
    void isExpired_positive_returnsFalse() {
        ActiveBuff buff = new ActiveBuff("NV_BASIC", StatType.STR, 50, 3);

        assertFalse(buff.isExpired());
    }

    @Test
    @DisplayName("decrementar reduz turnosRestantes em 1")
    void decrementar_reducesRemainingTurns() {
        ActiveBuff buff = new ActiveBuff("NV_BASIC", StatType.STR, 50, 3);

        ActiveBuff decrementado = buff.decrementar();

        assertEquals(2, decrementado.getTurnosRestantes());
        assertEquals(50, decrementado.getValue());
        assertEquals("NV_BASIC", decrementado.getSkillAegisName());
    }

    @Test
    @DisplayName("decrementar com turnosRestantes=-1 retorna o próprio objeto (buff permanente)")
    void decrementar_permanent_returnsSame() {
        ActiveBuff buff = new ActiveBuff("PROVOKE", StatType.STR, 10, -1);

        ActiveBuff resultado = buff.decrementar();

        assertSame(buff, resultado, "Buff permanente deve retornar a mesma instância");
    }
}
