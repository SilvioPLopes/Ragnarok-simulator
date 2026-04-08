package com.ragnarok.application.service;

import com.ragnarok.domain.model.EffectResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptInterpreterTest {

    private ScriptInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new ScriptInterpreter();
    }

    @Test
    void itemheal_fixo() {
        EffectResult r = interpreter.interpret("itemheal 100, 0;");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(100);
        assertThat(r.spHeal()).isEqualTo(0);
        assertThat(r.isPercent()).isFalse();
    }

    @Test
    void heal_com_espaco() {
        EffectResult r = interpreter.interpret("heal 50, 20;");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(50);
        assertThat(r.spHeal()).isEqualTo(20);
        assertThat(r.isPercent()).isFalse();
    }

    @Test
    void heal_com_parenteses() {
        EffectResult r = interpreter.interpret("heal(50, 20)");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(50);
        assertThat(r.spHeal()).isEqualTo(20);
    }

    @Test
    void percentheal_retorna_isPercent_true() {
        EffectResult r = interpreter.interpret("percentheal 10, 5;");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(10);
        assertThat(r.spHeal()).isEqualTo(5);
        assertThat(r.isPercent()).isTrue();
    }

    @Test
    void itemheal_rand() {
        EffectResult r = interpreter.interpret("itemheal rand(50,100), 0;");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isBetween(50, 100);
    }

    @Test
    void heal_com_skill_lv_multiplicado() {
        EffectResult r = interpreter.interpret("heal(skill_lv * 100, 0)", 3);
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(300);
    }

    @Test
    void sc_start_retorna_unsupported() {
        EffectResult r = interpreter.interpret("sc_start SC_INCREASEAGI, 60, 3, 0;");
        assertThat(r.supported()).isFalse();
    }

    @Test
    void script_null_retorna_unsupported() {
        assertThat(interpreter.interpret(null).supported()).isFalse();
    }

    @Test
    void script_vazio_retorna_unsupported() {
        assertThat(interpreter.interpret("").supported()).isFalse();
    }

    @Test
    void comando_desconhecido_retorna_unsupported() {
        assertThat(interpreter.interpret("unknown 1, 2;").supported()).isFalse();
    }

    // --- evaluateFormula ---

    @Test
    void formula_multiplicacao_simples() {
        // ATK=100, skill_lv=3 → 100 * 3 * 1.3 = 390
        Map<String, Integer> vars = Map.of("ATK", 100, "skill_lv", 3);
        assertThat(interpreter.evaluateFormula("ATK * skill_lv * 1.3", vars)).isEqualTo(390);
    }

    @Test
    void formula_soma_de_stats() {
        // MATK=200, INT=20, skill_lv=2 → 200*2 + 20*5 = 500
        Map<String, Integer> vars = Map.of("MATK", 200, "INT", 20, "skill_lv", 2);
        assertThat(interpreter.evaluateFormula("MATK * skill_lv + INT * 5", vars)).isEqualTo(500);
    }

    @Test
    void formula_com_parenteses() {
        // STR=10, DEX=8, skill_lv=3 → (10*2 + 8) * 3 = 84
        Map<String, Integer> vars = Map.of("STR", 10, "DEX", 8, "skill_lv", 3);
        assertThat(interpreter.evaluateFormula("(STR * 2 + DEX) * skill_lv", vars)).isEqualTo(84);
    }

    @Test
    void formula_buff_simples() {
        // skill_lv=5 → 5 * 3 = 15
        Map<String, Integer> vars = Map.of("skill_lv", 5);
        assertThat(interpreter.evaluateFormula("skill_lv * 3", vars)).isEqualTo(15);
    }

    @Test
    void formula_nula_retorna_zero() {
        assertThat(interpreter.evaluateFormula(null, Map.of())).isEqualTo(0);
        assertThat(interpreter.evaluateFormula("", Map.of())).isEqualTo(0);
    }

    @Test
    void formula_variavel_nao_existente_usa_zero() {
        // STR não existe no map → 0 * 5 = 0
        Map<String, Integer> vars = Map.of("skill_lv", 3);
        assertThat(interpreter.evaluateFormula("STR * 5", vars)).isEqualTo(0);
    }

    @Test
    void itemheal_com_chaves_formato_bRO() {
        // Scripts do bRO são envolvidos em { ... } — deve ser interpretado corretamente
        EffectResult r = interpreter.interpret("{ itemheal 50,0; }");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(50);
        assertThat(r.spHeal()).isEqualTo(0);
    }

    @Test
    void heal_com_chaves_formato_bRO() {
        EffectResult r = interpreter.interpret("{ heal 30, 10; }");
        assertThat(r.supported()).isTrue();
        assertThat(r.hpHeal()).isEqualTo(30);
        assertThat(r.spHeal()).isEqualTo(10);
    }
}
