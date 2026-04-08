package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.RathenaNpcScriptData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RathenaNpcScriptParser {

    private static final Logger log = LoggerFactory.getLogger(RathenaNpcScriptParser.class);

    // Linha de definição: mapName,x,y,dir<TAB>script<TAB>Name#label<TAB>spriteId,{
    // Ignora templates ("-") e requer coordenadas numéricas no início
    private static final Pattern SCRIPT_LINE = Pattern.compile(
            "^([\\w@]+),(\\d+),(\\d+),\\d+\\s+script\\s+(\\S+)\\s+(\\d+),\\{\\s*$"
    );

    // mes "texto";
    private static final Pattern MES_PATTERN = Pattern.compile(
            "mes\\s+\"(.*?)\"\\s*;"
    );

    // Códigos de cor: ^RRGGBB
    private static final Pattern COLOR_CODE = Pattern.compile("\\^[0-9A-Fa-f]{6}");

    /**
     * Parseia todos os .txt nos subdiretórios relevantes de rootDir.
     * Subdiretórios: cities, kafras, jobs, other, merchants
     */
    public List<RathenaNpcScriptData> parseDirectory(Path rootDir) throws IOException {
        List<RathenaNpcScriptData> results = new ArrayList<>();

        if (!Files.exists(rootDir)) {
            log.warn("RathenaNpcScriptParser: diretório não encontrado: {}", rootDir);
            return results;
        }

        List<Path> txtFiles = new ArrayList<>();
        for (String subdir : List.of("cities", "kafras", "jobs", "other", "merchants")) {
            Path dir = rootDir.resolve(subdir);
            if (!Files.exists(dir)) continue;
            Files.walk(dir, 1)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(txtFiles::add);
        }

        for (Path file : txtFiles) {
            try {
                results.addAll(parseFile(file));
            } catch (IOException e) {
                log.warn("RathenaNpcScriptParser: erro ao parsear {}: {}", file.getFileName(), e.getMessage());
            }
        }

        log.info("RathenaNpcScriptParser: {} entradas parseadas de {} arquivos.", results.size(), txtFiles.size());
        return results;
    }

    /**
     * Parseia um único arquivo .txt do rAthena.
     * Package-private para facilitar testes unitários.
     */
    List<RathenaNpcScriptData> parseFile(Path file) throws IOException {
        List<RathenaNpcScriptData> results = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        }

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher m = SCRIPT_LINE.matcher(line);
            if (!m.matches()) {
                i++;
                continue;
            }

            String mapName  = m.group(1);
            int    x        = Integer.parseInt(m.group(2));
            int    y        = Integer.parseInt(m.group(3));
            String rawLabel = m.group(4); // Name#label
            int    spriteId = Integer.parseInt(m.group(5));

            String name = rawLabel.contains("#")
                    ? rawLabel.substring(0, rawLabel.indexOf('#'))
                    : rawLabel;

            // Coletar linhas mes() até fechar o bloco
            List<String> mesTexts = new ArrayList<>();
            int braceDepth = 1;
            i++;
            while (i < lines.size() && braceDepth > 0) {
                String bodyLine = lines.get(i);
                for (char c : bodyLine.toCharArray()) {
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                }
                if (braceDepth > 0) {
                    Matcher mesMatcher = MES_PATTERN.matcher(bodyLine);
                    while (mesMatcher.find()) {
                        String text = COLOR_CODE.matcher(mesMatcher.group(1)).replaceAll("");
                        if (!text.isBlank()) {
                            mesTexts.add(text);
                        }
                    }
                }
                i++;
            }

            String dialog = mesTexts.isEmpty() ? null : String.join("\n", mesTexts);
            results.add(new RathenaNpcScriptData(mapName, x, y, name, spriteId, dialog));
        }

        return results;
    }
}
