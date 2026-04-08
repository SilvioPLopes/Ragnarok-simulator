package com.ragnarok.runner.populator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.infrastructure.persistence.NpcDialogEntity;
import com.ragnarok.infrastructure.persistence.NpcDialogRepository;
import com.ragnarok.infrastructure.persistence.NpcEntity;
import com.ragnarok.infrastructure.persistence.NpcRepository;
import com.ragnarok.infrastructure.persistence.NpcType;
import com.ragnarok.runner.populator.dto.RathenaNpcScriptData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class RathenaDialogPopulator {

    private static final Logger log = LoggerFactory.getLogger(RathenaDialogPopulator.class);

    @Value("${ro.assets.rathena-npc-path:}")
    private String rathenaNpcPath;

    private final NpcRepository       npcRepository;
    private final NpcDialogRepository npcDialogRepository;
    private final ObjectMapper        objectMapper;
    private final RathenaNpcScriptParser parser = new RathenaNpcScriptParser();

    public RathenaDialogPopulator(NpcRepository npcRepository,
                                  NpcDialogRepository npcDialogRepository,
                                  ObjectMapper objectMapper) {
        this.npcRepository       = npcRepository;
        this.npcDialogRepository = npcDialogRepository;
        this.objectMapper        = objectMapper;
    }

    public void run() throws IOException {
        if (rathenaNpcPath.isBlank()) {
            log.info("RathenaDialogPopulator: ro.assets.rathena-npc-path não configurado, pulando.");
            return;
        }

        List<RathenaNpcScriptData> entries = parser.parseDirectory(Path.of(rathenaNpcPath));
        log.info("RathenaDialogPopulator: {} entradas para processar.", entries.size());

        int updated = 0, created = 0, skipped = 0, dialogsSaved = 0;

        for (RathenaNpcScriptData data : entries) {
            if (data.dialog() == null && data.nodes().isEmpty()) {
                skipped++;
                continue;
            }

            Optional<NpcEntity> existing = npcRepository.findFirstByMapNameAndXAndY(
                    data.mapName(), data.x(), data.y());

            NpcEntity npc;
            if (existing.isPresent()) {
                npc = existing.get();
                if (data.dialog() != null) npc.setDialog(data.dialog());
                if (npc.getSpriteUrl() == null && data.spriteId() > 0) {
                    npc.setSpriteUrl("/ro-assets/output-npcs/" + data.spriteId() + "/0-0.png");
                }
                npcRepository.save(npc);
                updated++;
            } else {
                npc = new NpcEntity();
                npc.setSeedId("rathena_" + data.mapName() + "_" + data.x() + "_" + data.y());
                npc.setName(data.name());
                npc.setMapName(data.mapName());
                npc.setX(data.x());
                npc.setY(data.y());
                npc.setType(NpcType.NPC);
                npc.setDialog(data.dialog());
                if (data.spriteId() > 0) {
                    npc.setSpriteUrl("/ro-assets/output-npcs/" + data.spriteId() + "/0-0.png");
                }
                npc = npcRepository.save(npc);
                created++;
            }

            // Salvar árvore estruturada em npc_dialogs
            if (!data.nodes().isEmpty()) {
                try {
                    String nodesJson = objectMapper.writeValueAsString(data.nodes());
                    npcDialogRepository.deleteByNpcId(npc.getId());
                    NpcDialogEntity dialogEntity = new NpcDialogEntity();
                    dialogEntity.setNpcId(npc.getId());
                    dialogEntity.setNodes(nodesJson);
                    npcDialogRepository.save(dialogEntity);
                    dialogsSaved++;
                } catch (JsonProcessingException e) {
                    log.warn("RathenaDialogPopulator: erro ao serializar nodes NPC id={}: {}",
                            npc.getId(), e.getMessage());
                }
            }
        }

        log.info("RathenaDialogPopulator: {} atualizados, {} criados, {} ignorados, {} dialog trees salvas.",
                updated, created, skipped, dialogsSaved);
    }
}
