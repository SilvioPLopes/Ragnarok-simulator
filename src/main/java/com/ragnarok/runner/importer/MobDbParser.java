package com.ragnarok.runner.importer;

import com.ragnarok.infrastructure.persistence.MonsterEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MobDbParser {

    private static final Logger log = LoggerFactory.getLogger(MobDbParser.class);

    public List<MonsterEntity> parse(String yamlContent) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(50 * 1024 * 1024); // 50MB
        Yaml yaml = new Yaml(options);
        Map<String, Object> root = yaml.load(yamlContent);

        List<Map<String, Object>> body = (List<Map<String, Object>>) root.get("Body");
        if (body == null) return Collections.emptyList();

        return body.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private MonsterEntity toEntity(Map<String, Object> mob) {
        try {
            MonsterEntity entity = new MonsterEntity();

            entity.setId(toLong(mob.get("Id")));
            entity.setName((String) mob.get("Name"));
            entity.setHp(toInt(mob.get("Hp")));
            entity.setBaseExp(toInt(mob.get("BaseExp")));
            entity.setJobExp(toInt(mob.get("JobExp")));
            entity.setAttack(toInt(mob.get("Attack")));
            entity.setDef(toInt(mob.get("Defense")));
            entity.setMDef(toInt(mob.get("MagicDefense")));
            entity.setLevel(toInt(mob.get("Level")));

            entity.setStr(toInt(mob.get("Str")));
            entity.setAgi(toInt(mob.get("Agi")));
            entity.setVit(toInt(mob.get("Vit")));
            entity.setIntelligence(toInt(mob.get("Int")));
            entity.setDex(toInt(mob.get("Dex")));
            entity.setLuk(toInt(mob.get("Luk")));

            entity.setSize((String) mob.get("Size"));
            entity.setRace((String) mob.get("Race"));

            return entity;
        } catch (Exception e) {
            log.warn("Erro ao parsear monstro '{}': {}", mob.get("Name"), e.getMessage());
            return null;
        }
    }

    private Integer toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer i) return i;
        if (val instanceof Long l) return l.intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return 0; }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Long l) return l;
        try { return Long.parseLong(val.toString()); }
        catch (Exception e) { return null; }
    }
}