package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.NaviLinkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaviLinkLuaParser {

    private static final Logger log = LoggerFactory.getLogger(NaviLinkLuaParser.class);

    private static final Pattern STRING_LINE = Pattern.compile("^\\s*\"(.*?)\"\\s*,?\\s*$");
    private static final Pattern NUMBER_LINE = Pattern.compile("^\\s*(\\d+)\\s*,?\\s*$");

    /**
     * Parseia o arquivo navi_link_br.lua e retorna a lista de NaviLinkData.
     * O arquivo é UTF-16 LE com BOM.
     *
     * Campos por posição (0-indexed):
     *   0  = mapFrom  (String)
     *   1  = id       (Integer) — ignorar
     *   2  = tipo     (Integer) — ignorar
     *   3  = num      (Integer) — ignorar
     *   4  = name     (String)  — ignorar
     *   5  = empty    (String)  — ignorar
     *   6  = xFrom    (Integer)
     *   7  = yFrom    (Integer)
     *   8  = mapTo    (String)
     *   9  = xTo      (Integer)
     *   10 = yTo      (Integer)
     */
    public List<NaviLinkData> parse(String filePath) throws IOException {
        List<NaviLinkData> result = new ArrayList<>();
        List<String> lines = readUtf16Le(filePath);

        boolean inBlock = false;
        List<Object> fields = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("{")) {
                inBlock = true;
                fields.clear();
                continue;
            }

            if (trimmed.equals("}") || trimmed.equals("},")) {
                if (inBlock && !fields.isEmpty()) {
                    NaviLinkData data = buildEntry(fields);
                    if (data != null) {
                        result.add(data);
                    }
                }
                inBlock = false;
                fields.clear();
                continue;
            }

            if (!inBlock) continue;

            Matcher sm = STRING_LINE.matcher(line);
            if (sm.matches()) {
                fields.add(sm.group(1));
                continue;
            }

            Matcher nm = NUMBER_LINE.matcher(line);
            if (nm.matches()) {
                try {
                    fields.add(Integer.parseInt(nm.group(1)));
                } catch (NumberFormatException e) {
                    log.warn("NaviLinkLuaParser: número inválido na linha '{}', ignorando bloco.", trimmed);
                    inBlock = false;
                    fields.clear();
                }
                continue;
            }

            // Linha dentro do bloco que não é string nem número — ignorar silenciosamente
        }

        return result;
    }

    private NaviLinkData buildEntry(List<Object> fields) {
        if (fields.size() < 11) {
            log.warn("NaviLinkLuaParser: bloco com menos de 11 campos ignorado (campos={})", fields);
            return null;
        }
        try {
            String mapFrom = (String)  fields.get(0);
            // fields[1], [2], [3] ignorados
            // fields[4], [5] ignorados
            int    xFrom   = (Integer) fields.get(6);
            int    yFrom   = (Integer) fields.get(7);
            String mapTo   = (String)  fields.get(8);
            int    xTo     = (Integer) fields.get(9);
            int    yTo     = (Integer) fields.get(10);

            if (mapTo == null || mapTo.isEmpty()) {
                log.warn("NaviLinkLuaParser: mapTo vazio no bloco com mapFrom='{}', ignorando.", mapFrom);
                return null;
            }

            return new NaviLinkData(mapFrom, xFrom, yFrom, mapTo, xTo, yTo);
        } catch (ClassCastException e) {
            log.warn("NaviLinkLuaParser: tipos inesperados no bloco (campos={}), ignorando.", fields);
            return null;
        }
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
