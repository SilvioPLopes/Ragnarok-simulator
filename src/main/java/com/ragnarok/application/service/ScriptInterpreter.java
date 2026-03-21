package com.ragnarok.application.service;

import com.ragnarok.domain.model.EffectResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta scripts e fórmulas de skills.
 *
 * Suporta:
 * - itemheal(hp, sp), heal(hp, sp), percentheal(hpPct, spPct)  — consumíveis e cura
 * - evaluateFormula(expr, vars) — fórmulas de dano/buff com variáveis de stats
 *   Variáveis: STR, AGI, VIT, INT, DEX, LUK, ATK, MATK, DEF, MDEF, HP, MaxHP, MaxSP, skill_lv
 *   Operadores: +, -, *, /   Funções: rand(min, max)
 */
@Component
public class ScriptInterpreter {

    private static final Pattern RAND_PATTERN = Pattern.compile("rand\\((\\d+),(\\d+)\\)");
    private final Random rng = new Random();

    // ----------------------------------------------------------------
    // API pública — scripts de item/skill legados
    // ----------------------------------------------------------------

    public EffectResult interpret(String script) {
        return interpret(script, 1);
    }

    public EffectResult interpret(String script, int skillLevel) {
        if (script == null || script.isBlank()) return EffectResult.unsupported();

        String resolved = script.replace("skill_lv", String.valueOf(skillLevel));
        String trimmed = resolved.trim();

        int end = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '(') { end = i; break; }
        }
        String command = trimmed.substring(0, end).toLowerCase();

        return switch (command) {
            case "itemheal"    -> parseItemheal(trimmed);
            case "heal"        -> parseHeal(trimmed);
            case "percentheal" -> parsePercentheal(trimmed);
            case "sc_start"    -> EffectResult.unsupported();
            default            -> EffectResult.unsupported();
        };
    }

    // ----------------------------------------------------------------
    // API pública — fórmulas de dano/buff com variáveis de stats
    // ----------------------------------------------------------------

    /**
     * Avalia uma fórmula numérica com variáveis de stats do player.
     *
     * Exemplos de fórmulas:
     *   "ATK * skill_lv * 1.3"         → dano físico escalonado por nível
     *   "MATK * skill_lv + INT * 5"    → dano mágico com bônus de INT
     *   "(STR * 2 + DEX) * skill_lv"   → fórmula com parênteses
     *   "skill_lv * 3"                  → bônus de stat para buff
     *
     * @param formula   expressão em texto com variáveis
     * @param vars      mapa de variável → valor (STR, AGI, ATK, MATK, skill_lv, etc.)
     * @return          resultado inteiro truncado
     */
    public int evaluateFormula(String formula, Map<String, Integer> vars) {
        if (formula == null || formula.isBlank()) return 0;

        String expr = formula;

        // Substitui rand(a,b) por valor resolvido antes das variáveis
        expr = resolveRandInline(expr);

        // Substitui variáveis — ordena por tamanho decrescente para evitar
        // substituição parcial (ex: "MATK" antes de "ATK")
        List<String> sortedKeys = vars.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String key : sortedKeys) {
            expr = expr.replaceAll("(?i)\\b" + Pattern.quote(key) + "\\b",
                    String.valueOf(vars.get(key)));
        }

        try {
            return (int) evalArithmetic(expr.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ----------------------------------------------------------------
    // Avaliador de expressões aritméticas (recursive descent)
    // Suporta: inteiros, decimais, +, -, *, /, parênteses, espaços
    // ----------------------------------------------------------------

    private double evalArithmetic(String expr) {
        return new ArithmeticParser(expr).parse();
    }

    private static class ArithmeticParser {
        private final String expr;
        private int pos;

        ArithmeticParser(String expr) {
            this.expr = expr.replaceAll("\\s+", ""); // remove espaços
            this.pos = 0;
        }

        double parse() {
            double result = parseAddSub();
            if (pos != expr.length()) throw new IllegalArgumentException("Expressão inválida: " + expr);
            return result;
        }

        private double parseAddSub() {
            double left = parseMulDiv();
            while (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) {
                char op = expr.charAt(pos++);
                double right = parseMulDiv();
                left = (op == '+') ? left + right : left - right;
            }
            return left;
        }

        private double parseMulDiv() {
            double left = parseUnary();
            while (pos < expr.length() && (expr.charAt(pos) == '*' || expr.charAt(pos) == '/')) {
                char op = expr.charAt(pos++);
                double right = parseUnary();
                left = (op == '*') ? left * right : (right != 0 ? left / right : 0);
            }
            return left;
        }

        private double parseUnary() {
            if (pos < expr.length() && expr.charAt(pos) == '-') {
                pos++;
                return -parsePrimary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            if (pos < expr.length() && expr.charAt(pos) == '(') {
                pos++; // consume '('
                double val = parseAddSub();
                if (pos < expr.length() && expr.charAt(pos) == ')') pos++; // consume ')'
                return val;
            }
            return parseNumber();
        }

        private double parseNumber() {
            int start = pos;
            while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("Número esperado na posição " + pos + " em: " + expr);
            return Double.parseDouble(expr.substring(start, pos));
        }
    }

    // ----------------------------------------------------------------
    // Helpers internos
    // ----------------------------------------------------------------

    private String resolveRandInline(String expr) {
        Matcher m = RAND_PATTERN.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int max = Integer.parseInt(m.group(2));
            int val = min + rng.nextInt(max - min + 1);
            m.appendReplacement(sb, String.valueOf(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private EffectResult parseItemheal(String script) {
        String args = script.replaceFirst("(?i)^itemheal\\s*", "").replaceAll(";.*$", "").trim();
        int splitIndex = findTopLevelComma(args);
        if (splitIndex < 0) return EffectResult.unsupported();
        int hp = resolveArg(args.substring(0, splitIndex).trim());
        int sp = resolveArg(args.substring(splitIndex + 1).trim());
        return EffectResult.heal(hp, sp);
    }

    private EffectResult parseHeal(String script) {
        String inner = script.replaceFirst("(?i)^heal\\s*", "").replaceAll(";.*$", "").trim();
        if (inner.startsWith("(") && inner.endsWith(")")) {
            inner = inner.substring(1, inner.length() - 1).trim();
        }
        int splitIndex = findTopLevelComma(inner);
        if (splitIndex < 0) return EffectResult.unsupported();
        int hp = resolveArg(inner.substring(0, splitIndex).trim());
        int sp = resolveArg(inner.substring(splitIndex + 1).trim());
        return EffectResult.heal(hp, sp);
    }

    private EffectResult parsePercentheal(String script) {
        String args = script.replaceFirst("(?i)^percentheal\\s*", "").replaceAll(";.*$", "").trim();
        int splitIndex = findTopLevelComma(args);
        if (splitIndex < 0) return EffectResult.unsupported();
        int hpPct = resolveArg(args.substring(0, splitIndex).trim());
        int spPct = resolveArg(args.substring(splitIndex + 1).trim());
        return EffectResult.percentHeal(hpPct, spPct);
    }

    private int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    private int resolveArg(String arg) {
        if (arg.contains("*")) {
            try {
                String[] parts = arg.split("\\*");
                int result = 1;
                for (String p : parts) result *= Integer.parseInt(p.trim());
                return result;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        Matcher m = RAND_PATTERN.matcher(arg);
        if (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int max = Integer.parseInt(m.group(2));
            return min + rng.nextInt(max - min + 1);
        }
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
