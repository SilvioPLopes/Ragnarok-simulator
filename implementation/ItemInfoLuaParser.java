package [PACOTE_BASE].populator;

import [PACOTE_BASE].populator.dto.ItemClientData;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Faz o parse do iteminfo.lua (gerado pelo unluac a partir do iteminfo.lub do bRO).
 * O arquivo usa encoding EUC-KR pois os nomes de sprite são em coreano.
 */
public class ItemInfoLuaParser {

    private static final Pattern ITEM_ID_PATTERN =
            Pattern.compile("^\\s*\\[(\\d+)\\]\\s*=\\s*\\{");

    private static final Pattern STRING_FIELD_PATTERN =
            Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern DESC_START_PATTERN =
            Pattern.compile("identifiedDescriptionName\\s*=\\s*\\{");

    private static final Pattern DESC_LINE_PATTERN =
            Pattern.compile("\"([^\"]*)\"");

    public Map<Integer, ItemClientData> parse(String filePath) throws IOException {
        Map<Integer, ItemClientData> result = new HashMap<>();

        List<String> lines = Files.readAllLines(
                Paths.get(filePath),
                Charset.forName("EUC-KR")
        );

        int currentId = -1;
        String displayName = null;
        String resourceName = null;
        List<String> description = new ArrayList<>();
        boolean insideDescBlock = false;
        int braceDepth = 0;

        for (String line : lines) {

            Matcher idMatcher = ITEM_ID_PATTERN.matcher(line);
            if (idMatcher.find()) {
                if (currentId != -1) {
                    result.put(currentId, new ItemClientData(
                            currentId, displayName, resourceName, new ArrayList<>(description)
                    ));
                }
                currentId = Integer.parseInt(idMatcher.group(1));
                displayName = null;
                resourceName = null;
                description.clear();
                insideDescBlock = false;
                braceDepth = 1;
                continue;
            }

            if (currentId == -1) continue;

            if (DESC_START_PATTERN.matcher(line).find()) {
                insideDescBlock = true;
                continue;
            }

            if (insideDescBlock) {
                if (line.contains("}")) {
                    insideDescBlock = false;
                    continue;
                }
                Matcher descMatcher = DESC_LINE_PATTERN.matcher(line);
                if (descMatcher.find()) {
                    description.add(descMatcher.group(1));
                }
                continue;
            }

            Matcher fieldMatcher = STRING_FIELD_PATTERN.matcher(line);
            while (fieldMatcher.find()) {
                String key = fieldMatcher.group(1);
                String value = fieldMatcher.group(2);
                switch (key) {
                    case "identifiedDisplayName" -> displayName = value;
                    case "identifiedResourceName" -> resourceName = value;
                }
            }

            if (line.trim().startsWith("}")) {
                braceDepth--;
                if (braceDepth == 0 && currentId != -1) {
                    result.put(currentId, new ItemClientData(
                            currentId, displayName, resourceName, new ArrayList<>(description)
                    ));
                    currentId = -1;
                    displayName = null;
                    resourceName = null;
                    description.clear();
                }
            }
        }

        return result;
    }
}