package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.NaviNpcData;
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

public class NaviNpcLuaParser {

    private static final Logger log = LoggerFactory.getLogger(NaviNpcLuaParser.class);

    private static final Pattern STRING_LINE  = Pattern.compile("^\\s*\"(.*?)\"\\s*,?\\s*$");
    private static final Pattern NUMBER_LINE  = Pattern.compile("^\\s*(\\d+)\\s*,?\\s*$");

    /**
     * Parseia o arquivo navi_npc_br.lua e retorna a lista de NaviNpcData.
     * O arquivo é UTF-16 LE com BOM.
     */
    public List<NaviNpcData> parse(String filePath) throws IOException {
        List<NaviNpcData> result = new ArrayList<>();
        List<String> lines = readUtf16Le(filePath);

        boolean inBlock = false;
        // campos coletados dentro do bloco (posições 1..8)
        List<Object> fields = new ArrayList<>(); // String ou Integer

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("{")) {
                inBlock = true;
                fields.clear();
                continue;
            }

            if (trimmed.equals("}") || trimmed.equals("},")) {
                if (inBlock && !fields.isEmpty()) {
                    // Tentamos montar o DTO se temos campos suficientes
                    NaviNpcData data = buildEntry(fields);
                    if (data != null) {
                        result.add(data);
                    } else {
                        log.warn("NaviNpcLuaParser: bloco incompleto/malformado ignorado (campos={})", fields);
                    }
                }
                inBlock = false;
                fields.clear();
                continue;
            }

            if (!inBlock) continue;

            // Tenta string
            Matcher sm = STRING_LINE.matcher(line);
            if (sm.matches()) {
                fields.add(sm.group(1));
                continue;
            }

            // Tenta número
            Matcher nm = NUMBER_LINE.matcher(line);
            if (nm.matches()) {
                try {
                    fields.add(Integer.parseInt(nm.group(1)));
                } catch (NumberFormatException e) {
                    log.warn("NaviNpcLuaParser: número inválido na linha '{}', ignorando bloco.", trimmed);
                    inBlock = false;
                    fields.clear();
                }
                continue;
            }

            // Linha dentro do bloco que não é string nem número — ignorar silenciosamente
        }

        return result;
    }

    /**
     * Posições (0-indexed) dos 8 campos:
     *   0 = mapName      (String)
     *   1 = npcId        (Integer)
     *   2 = tipo         (Integer) — ignorado
     *   3 = spriteJobId  (Integer) — jobId direto para o sprite
     *   4 = name         (String)
     *   5 = spriteClass  (String)
     *   6 = x            (Integer)
     *   7 = y            (Integer)
     */
    private NaviNpcData buildEntry(List<Object> fields) {
        if (fields.size() < 8) return null;
        try {
            String mapName     = (String)  fields.get(0);
            int    npcId       = (Integer) fields.get(1);
            int    spriteJobId = (Integer) fields.get(3);
            String name        = (String)  fields.get(4);
            String spriteClass = (String)  fields.get(5);
            int    x           = (Integer) fields.get(6);
            int    y           = (Integer) fields.get(7);
            return new NaviNpcData(mapName, npcId, name, spriteClass, spriteJobId, x, y);
        } catch (ClassCastException e) {
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
