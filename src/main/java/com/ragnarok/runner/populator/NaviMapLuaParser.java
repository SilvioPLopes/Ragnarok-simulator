package com.ragnarok.runner.populator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class NaviMapLuaParser {

    private static final Logger log = LoggerFactory.getLogger(NaviMapLuaParser.class);

    /** Linha com exatamente uma string entre aspas, ex: `    "06guild_01",` */
    private static final Pattern STRING_LINE = Pattern.compile("^\\s*\"(.*?)\"\\s*,?\\s*$");

    /**
     * Parseia o arquivo navi_map_br.lua e retorna um map de mapId -> displayName.
     * O arquivo é UTF-16 LE com BOM.
     */
    public Map<String, String> parse(String filePath) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> lines = readUtf16Le(filePath);

        String pendingMapId = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Início de bloco: resetar estado
            if (trimmed.equals("{")) {
                pendingMapId = null;
                continue;
            }

            // Fim de bloco: descartar estado parcial se incompleto
            if (trimmed.equals("}") || trimmed.equals("},")) {
                pendingMapId = null;
                continue;
            }

            Matcher m = STRING_LINE.matcher(line);
            if (!m.matches()) {
                // Linha não é uma string — pode ser número ou estrutura; ignorar silenciosamente
                continue;
            }

            String value = m.group(1);

            if (pendingMapId == null) {
                pendingMapId = value;
            } else {
                result.put(pendingMapId, value);
                pendingMapId = null;
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
