package com.ragnarok.runner.populator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Copia PNGs já convertidos (icones-png/) para static/assets/items/,
 * renomeando pelo ID do item (ex: 501.png) para evitar nomes coreanos em URLs.
 *
 * Input:  C:/Users/silve/Documents/Sprites-Projeto/icones-png/
 * Output: src/main/resources/static/assets/items/
 */
public class SpriteConverter {

    private static final Logger log = LoggerFactory.getLogger(SpriteConverter.class);

    /**
     * @param pngInputDir      pasta com os PNGs fonte (nomes coreanos)
     * @param outputDir        pasta destino dentro do projeto
     * @param itemIdToResource mapa itemId → resourceName (vem do lua parser)
     * @return número de sprites copiados com sucesso
     */
    public int copyAndRenameByItemId(Path pngInputDir, Path outputDir,
                                     Map<Integer, String> itemIdToResource) throws IOException {
        Files.createDirectories(outputDir);

        // Indexa todos os PNGs do diretório de entrada: nome-sem-extensão (lowercase) → Path
        Map<String, Path> index = new HashMap<>();
        try (var stream = Files.walk(pngInputDir, 1)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                  .forEach(p -> {
                      String key = p.getFileName().toString()
                              .replaceAll("(?i)\\.png$", "").toLowerCase();
                      index.put(key, p);
                  });
        }
        log.info("SpriteConverter: {} PNGs indexados em {}", index.size(), pngInputDir);

        int copied = 0, missing = 0;
        for (Map.Entry<Integer, String> entry : itemIdToResource.entrySet()) {
            int itemId = entry.getKey();
            String resourceName = entry.getValue();
            if (resourceName == null || resourceName.isBlank()) continue;

            Path src = index.get(resourceName.toLowerCase());
            if (src == null) { missing++; continue; }

            Path dest = outputDir.resolve(itemId + ".png");
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }
        log.info("SpriteConverter: {} copiados, {} não encontrados.", copied, missing);
        return copied;
    }
}
