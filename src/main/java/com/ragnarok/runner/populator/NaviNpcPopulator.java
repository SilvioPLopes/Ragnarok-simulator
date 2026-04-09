package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.NpcEntity;
import com.ragnarok.infrastructure.persistence.NpcRepository;
import com.ragnarok.infrastructure.persistence.NpcType;
import com.ragnarok.runner.populator.dto.NaviNpcData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Importa todos os NPCs do navi_npc_br.lua para a tabela npcs.
 * Usa seedId = "navi_" + npcId como chave de idempotência.
 * Se o NPC já existir (pelo seedId), atualiza x, y, mapName, spriteRef.
 * Se não existir, cria novo com type=NPC.
 *
 * Ativado por: ro.assets.run-populator=true em application.properties.
 */
@Component
public class NaviNpcPopulator {

    private static final Logger log = LoggerFactory.getLogger(NaviNpcPopulator.class);

    @Value("${ro.assets.navi-npc-lua-path}")
    private String naviNpcLuaPath;

    private final NpcRepository npcRepository;
    private final NaviNpcLuaParser parser = new NaviNpcLuaParser();

    public NaviNpcPopulator(NpcRepository npcRepository) {
        this.npcRepository = npcRepository;
    }

    public void run() throws IOException {
        log.info("NaviNpcPopulator: lendo {} ...", naviNpcLuaPath);
        List<NaviNpcData> entries = parser.parse(naviNpcLuaPath);
        log.info("NaviNpcPopulator: {} entradas lidas.", entries.size());

        int created = 0;
        int updated = 0;

        for (NaviNpcData data : entries) {
            String seedId = "navi_" + data.npcId();

            NpcEntity npc;
            if (npcRepository.existsBySeedId(seedId)) {
                npc = npcRepository.findBySeedId(seedId).orElseThrow();
                updated++;
            } else {
                npc = new NpcEntity();
                npc.setSeedId(seedId);
                npc.setType(NpcType.NPC);
                created++;
            }

            npc.setName(data.name());
            npc.setMapName(data.mapName());
            npc.setX(data.x());
            npc.setY(data.y());
            npc.setSpriteRef(data.spriteClass());
            npc.setSpriteUrl("/ro-assets/output-npcs/" + data.spriteJobId() + "/0-0.png");

            npcRepository.save(npc);
        }

        log.info("NaviNpcPopulator: {} NPCs criados, {} atualizados.", created, updated);
    }
}
