package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.ItemClientData;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Faz o parse do iteminfo.lua (gerado pelo unluac a partir do iteminfo.lub do bRO).
 * Encoding EUC-KR pois os nomes de sprite são em coreano.
 * O arquivo real está em C:/Users/silve/Documents/Sprites-Projeto/item-info/iteminfo.lua
 */
public class ItemInfoLuaParser {

    private static final Pattern ITEM_ID      = Pattern.compile("^\\s*\\[(\\d+)\\]\\s*=\\s*\\{");
    private static final Pattern STRING_FIELD = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern DESC_START   = Pattern.compile("identifiedDescriptionName\\s*=\\s*\\{");
    private static final Pattern DESC_LINE    = Pattern.compile("\"([^\"]*)\"");

    public Map<Integer, ItemClientData> parse(String filePath) throws IOException {
        Map<Integer, ItemClientData> result = new LinkedHashMap<>();

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(filePath), Charset.forName("EUC-KR"));
        } catch (Exception e) {
            lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
        }

        int currentId = -1;
        String displayName = null, resourceName = null;
        List<String> description = new ArrayList<>();
        boolean insideDesc = false;
        int braceDepth = 0;

        for (String line : lines) {
            Matcher idMatcher = ITEM_ID.matcher(line);
            if (idMatcher.find()) {
                if (currentId != -1) flush(result, currentId, displayName, resourceName, description);
                currentId = Integer.parseInt(idMatcher.group(1));
                displayName = null; resourceName = null;
                description.clear(); insideDesc = false; braceDepth = 1;
                continue;
            }
            if (currentId == -1) continue;

            if (DESC_START.matcher(line).find()) { insideDesc = true; continue; }
            if (insideDesc) {
                if (line.contains("}")) { insideDesc = false; continue; }
                Matcher m = DESC_LINE.matcher(line);
                if (m.find()) description.add(m.group(1));
                continue;
            }

            Matcher f = STRING_FIELD.matcher(line);
            while (f.find()) {
                switch (f.group(1)) {
                    case "identifiedDisplayName"  -> displayName  = f.group(2);
                    case "identifiedResourceName" -> resourceName = f.group(2);
                }
            }

            if (line.trim().startsWith("}")) {
                braceDepth--;
                if (braceDepth == 0) {
                    flush(result, currentId, displayName, resourceName, description);
                    currentId = -1;
                }
            }
        }
        if (currentId != -1) flush(result, currentId, displayName, resourceName, description);
        return result;
    }

    private void flush(Map<Integer, ItemClientData> result, int id,
                       String display, String resource, List<String> desc) {
        result.put(id, new ItemClientData(id, display, resource, new ArrayList<>(desc)));
    }
}
