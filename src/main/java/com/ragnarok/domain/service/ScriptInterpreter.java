package com.ragnarok.domain.service;

import com.ragnarok.domain.model.EffectResult;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptInterpreter {

    private static final Pattern RAND_PATTERN = Pattern.compile("rand\\((\\d+),(\\d+)\\)");
    private final Random rng = new Random();

    public EffectResult interpret(String script) {
        if (script == null || script.isBlank()) {
            return EffectResult.unsupported();
        }

        String trimmed = script.trim();

        // Extrai comando principal (primeira palavra antes de espaço ou parêntese)
        int end = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '(') {
                end = i;
                break;
            }
        }
        String command = trimmed.substring(0, end).toLowerCase();

        return switch (command) {
            case "itemheal" -> parseItemheal(trimmed);
            default -> EffectResult.unsupported();
        };
    }

    private EffectResult parseItemheal(String script) {
        // Formato: itemheal <hp_arg>,<sp_arg>;
        // Remove "itemheal " do início e ";" do final
        String args = script.replaceFirst("(?i)^itemheal\\s*", "").replaceAll(";.*$", "").trim();

        // Divide nos dois argumentos (separados por vírgula fora de parênteses)
        int splitIndex = findTopLevelComma(args);
        if (splitIndex < 0) {
            return EffectResult.unsupported();
        }

        String hpArg = args.substring(0, splitIndex).trim();
        String spArg = args.substring(splitIndex + 1).trim();

        int hp = resolveArg(hpArg);
        int sp = resolveArg(spArg);

        return EffectResult.heal(hp, sp);
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
