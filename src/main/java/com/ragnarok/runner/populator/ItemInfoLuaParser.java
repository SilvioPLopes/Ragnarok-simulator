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
    private static final Pattern DESC_START   = Pattern.compile("(?<![a-zA-Z])identifiedDescriptionName\\s*=\\s*\\{");
    private static final Pattern DESC_LINE    = Pattern.compile("\"([^\"]*)\"");

    public Map<Integer, ItemClientData> parse(String filePath) throws IOException {
        Map<Integer, ItemClientData> result = new LinkedHashMap<>();

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(filePath), Charset.forName("EUC-KR"));
        } catch (Exception e) {
            System.err.println("WARN: EUC-KR failed, retrying as UTF-8: " + e.getMessage());
            lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
        }

        int currentId = -1;
        String displayName = null, resourceName = null;
        List<String> description = new ArrayList<>();
        boolean insideIdentifiedDesc = false;
        // Profundidade a partir da raiz do item (item-root = 1)
        int depth = 0;

        for (String line : lines) {
            // Novo item
            Matcher idMatcher = ITEM_ID.matcher(line);
            if (idMatcher.find()) {
                if (currentId != -1) flush(result, currentId, displayName, resourceName, description);
                currentId = Integer.parseInt(idMatcher.group(1));
                displayName = null; resourceName = null;
                description.clear(); insideIdentifiedDesc = false; depth = 1;
                continue;
            }
            if (currentId == -1) continue;

            // Conta abertura de sub-blocos (linhas com "=" e "{" = início de sub-bloco)
            boolean hasOpen  = line.contains("{");
            boolean hasClose = line.trim().startsWith("}") || line.trim().equals("},");

            if (hasOpen && !hasClose) {
                // Verifica se é o início do bloco de descrição identificada
                if (DESC_START.matcher(line).find()) insideIdentifiedDesc = true;
                depth++;
                continue;
            }

            if (insideIdentifiedDesc && depth == 2) {
                // Estamos dentro do bloco identifiedDescriptionName
                if (hasClose) {
                    insideIdentifiedDesc = false;
                    depth--;
                    if (depth == 0 && currentId != -1) {
                        flush(result, currentId, displayName, resourceName, description);
                        currentId = -1;
                    }
                    continue;
                }
                Matcher m = DESC_LINE.matcher(line);
                if (m.find()) description.add(m.group(1));
                continue;
            }

            if (hasClose && !hasOpen) {
                depth--;
                if (depth == 0 && currentId != -1) {
                    flush(result, currentId, displayName, resourceName, description);
                    currentId = -1;
                }
                continue;
            }

            // Extrai campos string
            Matcher f = STRING_FIELD.matcher(line);
            while (f.find()) {
                switch (f.group(1)) {
                    case "identifiedDisplayName"  -> displayName  = f.group(2);
                    case "identifiedResourceName" -> resourceName = f.group(2);
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
