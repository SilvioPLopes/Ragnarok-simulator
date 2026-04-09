package com.ragnarok.runner.populator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faz o parse do npcidentity_decompiled.lua gerado pelo unluac.
 * Encoding: UTF-16 LE com BOM (FF FE).
 * Extrai apenas constantes numéricas: "JT_NOME = 123,"
 */
public class NpcLuaParser {

    private static final Pattern CONSTANT = Pattern.compile("^\\s*(\\w+)\\s*=\\s*(\\d+),?\\s*$");

    /**
     * @param filePath caminho para npcidentity_decompiled.lua
     * @return Map de nome-da-constante para jobId (ex: "JT_4_F_KAFRA1" -> 117)
     */
    public Map<String, Integer> parseNpcIdentity(String filePath) throws IOException {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String line : readUtf16Le(filePath)) {
            Matcher m = CONSTANT.matcher(line);
            if (m.matches()) {
                result.put(m.group(1), Integer.parseInt(m.group(2)));
            }
        }
        return result;
    }

    private List<String> readUtf16Le(String filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        int offset = (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xFE) ? 2 : 0;
        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_16LE);
        return Arrays.asList(text.split("\\r?\\n"));
    }
}
