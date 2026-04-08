package com.ragnarok.runner.populator;

import com.ragnarok.domain.model.NpcDialogNode;
import com.ragnarok.runner.populator.dto.RathenaNpcScriptData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // select("A:B") ou switch(select("A:B"))
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "select\\s*\\(\\s*\"(.*?)\"\\s*\\)"
    );

    // case N:
    private static final Pattern CASE_PATTERN = Pattern.compile(
            "^\\s*case\\s+\\d+\\s*:\\s*$"
    );

    // getitem itemId, amount
    private static final Pattern GETITEM_PATTERN = Pattern.compile(
            "getitem\\s+(\\d+)\\s*,\\s*(\\d+)"
    );

    // [Nome] — speaker indicator no primeiro mes()
    private static final Pattern SPEAKER_PATTERN = Pattern.compile(
            "^\\[(.+?)]$"
    );

    /**
     * Parseia todos os .txt em rootDir recursivamente.
     * Exclui subdiretórios que não contêm scripts de NPC interativos:
     * scripts_custom, script_addons, re (renewal-only), pre-re.
     */
    public List<RathenaNpcScriptData> parseDirectory(Path rootDir) throws IOException {
        List<RathenaNpcScriptData> results = new ArrayList<>();

        if (!Files.exists(rootDir)) {
            log.warn("RathenaNpcScriptParser: diretório não encontrado: {}", rootDir);
            return results;
        }

        List<Path> txtFiles = Files.walk(rootDir)
                .filter(p -> p.toString().endsWith(".txt"))
                .filter(p -> {
                    String rel = rootDir.relativize(p).toString().replace('\\', '/');
                    // Ignora diretórios de scripts customizados e addons que não são NPCs padrão
                    return !rel.startsWith("scripts_custom/")
                        && !rel.startsWith("script_addons/")
                        && !rel.startsWith("re/")
                        && !rel.startsWith("pre-re/");
                })
                .collect(java.util.stream.Collectors.toList());

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

            // Coletar body lines e mes texts até fechar o bloco
            List<String> bodyLines = new ArrayList<>();
            List<String> mesTexts  = new ArrayList<>();
            int braceDepth = 1;
            i++;
            while (i < lines.size() && braceDepth > 0) {
                String bodyLine = lines.get(i);
                for (char c : bodyLine.toCharArray()) {
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                }
                if (braceDepth > 0) {
                    bodyLines.add(bodyLine);
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
            List<NpcDialogNode> rawNodes = buildNodes(bodyLines);
            // Se não há diálogo (apenas close sem mes), expõe lista vazia
            List<NpcDialogNode> nodes = mesTexts.isEmpty() ? List.of() : rawNodes;
            results.add(new RathenaNpcScriptData(mapName, x, y, name, spriteId, dialog, nodes));
        }

        return results;
    }

    // ─── Node builder ──────────────────────────────────────────────────────────

    List<NpcDialogNode> buildNodes(List<String> lines) {
        List<NpcDialogNode> nodes   = new ArrayList<>();
        List<String>        pending = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("//")) { i++; continue; }

            // mes "texto";
            Matcher mes = MES_PATTERN.matcher(line);
            if (mes.find()) {
                String text = COLOR_CODE.matcher(mes.group(1)).replaceAll("");
                if (!text.isBlank()) pending.add(text);
                i++; continue;
            }

            // next;
            if (line.equals("next;") || line.equals("next")) {
                if (!pending.isEmpty()) {
                    nodes.add(makeDialogNode(pending, nodes.size() + 1));
                    pending.clear();
                }
                i++; continue;
            }

            // close / close2
            if (line.startsWith("close")) {
                flushPending(nodes, pending);
                nodes.add(new NpcDialogNode.ActionNode("close", null));
                return nodes;
            }

            // select("A:B") ou switch(select("A:B")) {
            Matcher sel = SELECT_PATTERN.matcher(line);
            if (sel.find()) {
                flushPending(nodes, pending);
                String[] labels   = sel.group(1).split(":");
                boolean hasSwitch = line.contains("switch");
                int     menuIdx   = nodes.size();
                nodes.add(null); // placeholder

                List<NpcDialogNode.MenuNode.Choice> choices = new ArrayList<>();

                if (hasSwitch) {
                    i++; // avança past a linha do switch
                    if (i < lines.size() && lines.get(i).trim().equals("{")) i++;

                    for (int c = 0; c < labels.length; c++) {
                        while (i < lines.size()
                               && !CASE_PATTERN.matcher(lines.get(i).trim()).matches()) i++;
                        if (i >= lines.size()) break;
                        i++; // pula "case N:"

                        List<String> caseBody = new ArrayList<>();
                        int depth = 0;
                        while (i < lines.size()) {
                            String cl = lines.get(i).trim();
                            if (depth == 0
                                && (CASE_PATTERN.matcher(cl).matches() || cl.equals("}"))) break;
                            for (char ch : cl.toCharArray()) {
                                if (ch == '{') depth++;
                                else if (ch == '}') depth--;
                            }
                            if (!cl.equals("break;")) caseBody.add(lines.get(i));
                            i++;
                        }

                        int                 caseStart = nodes.size();
                        List<NpcDialogNode> caseNodes = rebase(buildNodes(caseBody), caseStart);
                        nodes.addAll(caseNodes);
                        choices.add(new NpcDialogNode.MenuNode.Choice(
                                labels[c].trim(), String.valueOf(caseStart)));
                    }

                    while (i < lines.size() && lines.get(i).trim().equals("}")) i++;

                } else {
                    int closeIdx = nodes.size();
                    nodes.add(new NpcDialogNode.ActionNode("close", null));
                    for (String label : labels) {
                        choices.add(new NpcDialogNode.MenuNode.Choice(
                                label.trim(), String.valueOf(closeIdx)));
                    }
                    i++;
                }

                nodes.set(menuIdx, new NpcDialogNode.MenuNode(List.of(), choices));
                continue;
            }

            // if (...) { → entra no bloco, ignora a condição
            if (line.startsWith("if") && line.contains("{")) { i++; continue; }

            // } / } else { / else { → pula
            if (line.equals("}") || line.startsWith("} else") || line.startsWith("else")) {
                i++; continue;
            }

            // heal
            if (line.startsWith("heal")) {
                flushPending(nodes, pending);
                nodes.add(new NpcDialogNode.ActionNode("heal", null));
                i++; continue;
            }

            // getitem itemId, amount
            Matcher getitem = GETITEM_PATTERN.matcher(line);
            if (getitem.find()) {
                flushPending(nodes, pending);
                Map<String, Object> params = new HashMap<>();
                params.put("itemId", Integer.parseInt(getitem.group(1)));
                params.put("amount", Integer.parseInt(getitem.group(2)));
                nodes.add(new NpcDialogNode.ActionNode("getitem", params));
                i++; continue;
            }

            i++; // ignora linha desconhecida
        }

        // flush remaining (script sem close explícito)
        if (!pending.isEmpty()) {
            flushPending(nodes, pending);
            nodes.add(new NpcDialogNode.ActionNode("close", null));
        }

        return nodes;
    }

    private NpcDialogNode.DialogNode makeDialogNode(List<String> texts, int next) {
        String speaker = null;
        List<String> body = texts;
        if (!texts.isEmpty()) {
            Matcher m = SPEAKER_PATTERN.matcher(texts.get(0));
            if (m.matches()) {
                speaker = m.group(1);
                body = texts.subList(1, texts.size());
            }
        }
        return new NpcDialogNode.DialogNode(speaker, List.copyOf(body), next);
    }

    private void flushPending(List<NpcDialogNode> nodes, List<String> pending) {
        if (!pending.isEmpty()) {
            nodes.add(makeDialogNode(List.copyOf(pending), nodes.size() + 1));
            pending.clear();
        }
    }

    private List<NpcDialogNode> rebase(List<NpcDialogNode> input, int offset) {
        List<NpcDialogNode> result = new ArrayList<>();
        for (NpcDialogNode n : input) {
            if (n instanceof NpcDialogNode.DialogNode d) {
                result.add(new NpcDialogNode.DialogNode(
                    d.speaker(), d.texts(),
                    d.next() != null ? d.next() + offset : null));
            } else if (n instanceof NpcDialogNode.MenuNode m) {
                List<NpcDialogNode.MenuNode.Choice> rebased = new ArrayList<>();
                for (NpcDialogNode.MenuNode.Choice c : m.choices()) {
                    try {
                        rebased.add(new NpcDialogNode.MenuNode.Choice(
                            c.label(), String.valueOf(Integer.parseInt(c.next()) + offset)));
                    } catch (NumberFormatException e) {
                        rebased.add(c); // mantém "__shop__" / "__close__" intactos
                    }
                }
                result.add(new NpcDialogNode.MenuNode(m.texts(), rebased));
            } else {
                result.add(n); // ActionNode — sem índices, não precisa rebase
            }
        }
        return result;
    }
}
