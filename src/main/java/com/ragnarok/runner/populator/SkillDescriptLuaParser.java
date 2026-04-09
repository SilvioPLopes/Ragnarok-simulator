package com.ragnarok.runner.populator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faz o parse do skilldescript.lua do cliente bRO.
 * Encoding: ISO-8859-1 (Windows-1252).
 * Extrai mapeamento de CONSTANT_NAME → lista de linhas de descrição (sem color codes).
 */
public class SkillDescriptLuaParser {

    private static final Pattern SKID_KEY    = Pattern.compile("^\\s*\\[SKID\\.([\\w]+)\\]\\s*=?\\s*\\{?\\s*$");
    private static final Pattern QUOTED_LINE = Pattern.compile("^\\s*\"(.*?)\"\\s*,?\\s*$");
    private static final Pattern COLOR_CODE  = Pattern.compile("\\^[0-9a-fA-F]{6}");

    /**
     * @param filePath caminho para skilldescript.lua
     * @return Map de CONSTANT_NAME para lista de linhas de descrição
     */
    public Map<String, List<String>> parse(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath), Charset.forName("ISO-8859-1"));
        Map<String, List<String>> result = new LinkedHashMap<>();

        String currentConstant = null;
        List<String> currentLines = null;

        for (String line : lines) {
            Matcher keyMatcher = SKID_KEY.matcher(line);
            if (keyMatcher.matches()) {
                // Flush entrada anterior
                if (currentConstant != null && currentLines != null && !currentLines.isEmpty()) {
                    result.put(currentConstant, currentLines);
                }
                currentConstant = keyMatcher.group(1);
                currentLines = new ArrayList<>();
                continue;
            }

            if (currentConstant != null) {
                Matcher quotedMatcher = QUOTED_LINE.matcher(line);
                if (quotedMatcher.matches()) {
                    String content = quotedMatcher.group(1);
                    content = COLOR_CODE.matcher(content).replaceAll("");
                    currentLines.add(content);
                }
            }
        }

        // Flush última entrada
        if (currentConstant != null && currentLines != null && !currentLines.isEmpty()) {
            result.put(currentConstant, currentLines);
        }

        return result;
    }
}
