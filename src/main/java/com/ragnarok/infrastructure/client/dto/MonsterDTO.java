package com.ragnarok.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MonsterDTO(
        String _id,
        Long monster_id,
        String monster_info,
        String size,
        String race,
        String type,
        Integer element_power,
        String gif,
        MainAtbDTO main_atb,
        MainStatsDTO main_stats,
        ElementalDamageDTO elementalDamage,
        SkillsDTO skills,
        List<DropDTO> drops,
        List<MapDTO> maps
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MainAtbDTO(Integer agi, Integer int_val, Integer luk, Integer vit, Integer dex) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MainStatsDTO(
            // TUDO VIRQU STRING PARA ACEITAR QUALQUER COISA DA API
            String hp,
            String level,
            String def,
            String m_def,
            String attack,
            String magic_attack,
            String aspd,
            String move_speed,
            String base_exp,
            String job_exp,
            String flee,
            String hit,
            String defense_rating,
            String crit_shield,
            String exp_ratio
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementalDamageDTO(
            Integer neutral, Integer poison, Integer earth, Integer shadow, Integer water,
            Integer undead, Integer fire, Integer holy, Integer wind, Integer ghost
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DropDTO(String name, String img, Double rate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MapDTO(String name, Integer number, Integer amount, String frequency, String type, String img) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillsDTO(List<SpellDTO> spell) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpellDTO(String name, Integer level) {}
}