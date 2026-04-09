package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.SkillEntity;
import com.ragnarok.infrastructure.persistence.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enriquece as skills já importadas do rAthena com dados do cliente bRO:
 * nome de exibição PT-BR e descrição.
 *
 * Pré-requisito: Skills já importadas na tabela skills.
 * Ativado por: ro.assets.run-populator=true em application.properties.
 */
@Component
public class SkillClientDataPopulator {

    private static final Logger log = LoggerFactory.getLogger(SkillClientDataPopulator.class);

    private final SkillRepository skillRepository;
    private final NpcLuaParser npcLuaParser = new NpcLuaParser();
    private final SkillInfoListLuaParser skillInfoListParser = new SkillInfoListLuaParser();
    private final SkillDescriptLuaParser skillDescriptParser = new SkillDescriptLuaParser();

    @Value("${ro.assets.skill-identity-lua-path}")
    private String skillIdLuaPath;

    @Value("${ro.assets.skillinfolist-lua-path}")
    private String skillInfoListLuaPath;

    @Value("${ro.assets.skilldescript-lua-path}")
    private String skillDescriptLuaPath;

    public SkillClientDataPopulator(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * Lê os três arquivos Lua do cliente e atualiza name e description
     * nas skills existentes no banco.
     */
    public void run() throws IOException {
        log.info("Lendo skillid.lua em {}...", skillIdLuaPath);
        Map<String, Integer> skillIds = npcLuaParser.parseNpcIdentity(skillIdLuaPath);
        log.info("skillid.lua: {} constantes lidas.", skillIds.size());

        log.info("Lendo skillinfolist.lua em {}...", skillInfoListLuaPath);
        Map<String, String> infoList = skillInfoListParser.parse(skillInfoListLuaPath);
        log.info("skillinfolist.lua: {} skills lidas.", infoList.size());

        log.info("Lendo skilldescript.lua em {}...", skillDescriptLuaPath);
        Map<String, List<String>> descript = skillDescriptParser.parse(skillDescriptLuaPath);
        log.info("skilldescript.lua: {} descrições lidas.", descript.size());

        int updatedName = 0;
        int updatedDescription = 0;
        int notFound = 0;

        for (Map.Entry<String, Integer> entry : skillIds.entrySet()) {
            String constantName = entry.getKey();
            long skillId = entry.getValue();

            Optional<SkillEntity> skillOpt = skillRepository.findById(skillId);
            if (skillOpt.isEmpty()) {
                log.debug("Skill não encontrada no banco: {} (id={})", constantName, skillId);
                notFound++;
                continue;
            }

            SkillEntity skill = skillOpt.get();
            boolean changed = false;

            String displayName = infoList.get(constantName);
            if (displayName != null) {
                skill.setName(displayName);
                updatedName++;
                changed = true;
            }

            List<String> lines = descript.get(constantName);
            if (lines != null && !lines.isEmpty()) {
                skill.setDescription(String.join("\n", lines));
                updatedDescription++;
                changed = true;
            }

            if (changed) {
                skillRepository.save(skill);
            }
        }

        log.info("SkillClientDataPopulator: {} names atualizados, {} descriptions atualizadas, {} não encontradas no banco.",
                updatedName, updatedDescription, notFound);
    }
}
