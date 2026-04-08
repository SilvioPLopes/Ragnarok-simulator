package com.ragnarok.runner.populator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faz o parse do skillinfolist.lua do cliente bRO.
 * Encoding: ISO-8859-1 (Windows-1252).
 * Extrai mapeamento de CONSTANT_NAME → nome de exibição em PT-BR.
 */
public class SkillInfoListLuaParser {

    private static final Pattern SKID_KEY   = Pattern.compile("^\\s*\\[SKID\\.([\\w]+)\\]\\s*=?\\s*\\{?\\s*$");
    private static final Pattern SKILL_NAME = Pattern.compile("SkillName\\s*=\\s*\\[\\[([^\\]]*?)\\]\\]");

    /**
     * @param filePath caminho para skillinfolist.lua
     * @return Map de CONSTANT_NAME para nome de exibição PT-BR
     */
    public Map<String, String> parse(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath), Charset.forName("ISO-8859-1"));
        Map<String, String> result = new LinkedHashMap<>();

        String currentConstant = null;
        String currentDisplayName = null;

        for (String line : lines) {
            Matcher keyMatcher = SKID_KEY.matcher(line);
            if (keyMatcher.matches()) {
                // Flush entrada anterior
                if (currentConstant != null && currentDisplayName != null) {
                    result.put(currentConstant, currentDisplayName);
                }
                currentConstant = keyMatcher.group(1);
                currentDisplayName = null;
                continue;
            }

            if (currentConstant != null) {
                Matcher nameMatcher = SKILL_NAME.matcher(line);
                if (nameMatcher.find()) {
                    currentDisplayName = nameMatcher.group(1);
                }
            }
        }

        // Flush última entrada
        if (currentConstant != null && currentDisplayName != null) {
            result.put(currentConstant, currentDisplayName);
        }

        return result;
    }
}
