package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.NpcEntity;
import com.ragnarok.infrastructure.persistence.NpcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enriquece os NPCs já seedados com URLs de sprite locais.
 *
 * Pré-requisito: NpcSeedLoader já rodou (tabela npcs populada).
 * Pré-requisito: zrenderer gerou output-npcs/ a partir dos job IDs abaixo.
 * Ativado por: ro.assets.run-populator=true em application.properties.
 */
@Component
public class NpcSpritePopulator {

    private static final Logger log = LoggerFactory.getLogger(NpcSpritePopulator.class);

    /** Bridge: spriteRef do nosso seed -> constante JT_* do cliente bRO. */
    private static final Map<String, String> SPRITE_REF_TO_JT = Map.of(
            "kafra",         "JT_4_F_KAFRA1",
            "warp_portal",   "JT_WARPNPC",
            "npc_generic",   "JT_1_F_01",
            "shop_generic",  "JT_1_F_MERCHANT_01"
    );

    @Value("${ro.assets.npc-identity-lua-path}")
    private String npcIdentityLuaPath;

    private final NpcRepository npcRepository;
    private final NpcLuaParser parser = new NpcLuaParser();

    public NpcSpritePopulator(NpcRepository npcRepository) {
        this.npcRepository = npcRepository;
    }

    /** Ponto de entrada: parse lua -> construir map -> atualizar banco. */
    public void run() throws IOException {
        log.info("NpcSpritePopulator: lendo {} ...", npcIdentityLuaPath);
        Map<String, Integer> constantToJobId = parser.parseNpcIdentity(npcIdentityLuaPath);
        log.info("NpcSpritePopulator: {} constantes lidas.", constantToJobId.size());

        List<NpcEntity> npcs = npcRepository.findAll();
        Map<String, String> spriteRefToUrl = buildSpriteUrlMap(constantToJobId, npcs);

        int updated = 0;
        int unresolved = 0;
        for (NpcEntity npc : npcs) {
            if (npc.getSpriteRef() == null) continue;
            String url = spriteRefToUrl.get(npc.getSpriteRef());
            if (url == null) {
                unresolved++;
                continue;
            }
            npc.setSpriteUrl(url);
            npcRepository.save(npc);
            updated++;
        }
        log.info("NpcSpritePopulator: {} NPCs atualizados, {} sem match de sprite.", updated, unresolved);
    }

    /**
     * Constrói o map spriteRef -> URL usando a bridge SPRITE_REF_TO_JT (fallback para refs especiais)
     * e match dinâmico para todos os spriteRefs dos NPCs do banco.
     * Package-private para facilitar testes.
     */
    Map<String, String> buildSpriteUrlMap(Map<String, Integer> constantToJobId, List<NpcEntity> npcs) {
        Map<String, String> result = new LinkedHashMap<>();

        // 1. Bridge hardcoded (fallback para refs especiais)
        for (Map.Entry<String, String> entry : SPRITE_REF_TO_JT.entrySet()) {
            Integer jobId = constantToJobId.get(entry.getValue());
            if (jobId == null) {
                log.warn("NpcSpritePopulator: constante {} nao encontrada no lua (spriteRef={})",
                        entry.getValue(), entry.getKey());
                continue;
            }
            result.put(entry.getKey(), "/ro-assets/output-npcs/" + jobId + "/0-0.png");
        }

        // 2. Match dinâmico para todos os spriteRefs do banco
        for (NpcEntity npc : npcs) {
            String spriteRef = npc.getSpriteRef();
            if (spriteRef == null || result.containsKey(spriteRef)) continue;

            // Tentar JT_<SPRITE_REF_UPPERCASE>
            Integer jobId = constantToJobId.get("JT_" + spriteRef.toUpperCase());
            if (jobId == null) {
                // Tentar sem prefixo (nome direto)
                jobId = constantToJobId.get(spriteRef.toUpperCase());
            }
            if (jobId != null) {
                result.put(spriteRef, "/ro-assets/output-npcs/" + jobId + "/0-0.png");
            }
        }
        return result;
    }
}
