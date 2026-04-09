package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.NaviMobData;
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

public class NaviMobLuaParser {

    private static final Logger log = LoggerFactory.getLogger(NaviMobLuaParser.class);

    private static final Pattern STRING_LINE = Pattern.compile("^\\s*\"(.*?)\"\\s*,?\\s*$");
    private static final Pattern NUMBER_LINE = Pattern.compile("^\\s*(\\d+)\\s*,?\\s*$");

    /**
     * Parseia o arquivo navi_mob_br.lua e retorna a lista de NaviMobData.
     * O arquivo é UTF-16 LE com BOM.
     *
     * Campos por bloco (0-indexed):
     *   0 = mapName   (String)
     *   1 = entryId   (Integer) — ignorar
     *   2 = amount    (Integer)
     *   3 = hash      (Integer/Long) — ignorar
     *   4 = ptBrName  (String)
     *   5 = aegisName (String)
     *   6 = x         (Integer) — ignorar
     *   7 = packed    (Integer) — ignorar
     */
    public List<NaviMobData> parse(String filePath) throws IOException {
        List<NaviMobData> result = new ArrayList<>();
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
                    NaviMobData data = buildEntry(fields);
                    if (data != null) {
                        result.add(data);
                    } else {
                        log.warn("NaviMobLuaParser: bloco incompleto/malformado ignorado (campos={})", fields);
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
                    log.warn("NaviMobLuaParser: número inválido na linha '{}', ignorando bloco.", trimmed);
                    inBlock = false;
                    fields.clear();
                }
                continue;
            }

            // Linha dentro do bloco que não é string nem número — ignorar silenciosamente
        }

        return result;
    }

    private NaviMobData buildEntry(List<Object> fields) {
        if (fields.size() < 6) return null;
        try {
            String mapName   = (String)  fields.get(0);
            // fields[1] = entryId — ignorar
            int    amount    = (Integer) fields.get(2);
            // fields[3] = hash — ignorar
            String ptBrName  = (String)  fields.get(4);
            String aegisName = (String)  fields.get(5);
            return new NaviMobData(mapName, amount, ptBrName, aegisName);
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
