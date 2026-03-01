package com.ragnarok.infrastructure.client.mapper;

import com.ragnarok.domain.model.*;
import com.ragnarok.infrastructure.client.dto.MonsterDTO;
import com.ragnarok.infrastructure.persistence.MonsterDropEntity;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MonsterMapper {

    private final ItemMapper itemMapper;

    // Injeção de dependência obrigatória para converter os itens dentro dos drops
    public MonsterMapper(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    // 1. API (DTO) -> Domain
    public Monster toDomain(MonsterDTO dto) {
        if (dto == null) return null;

        Monster monster = new Monster();
        monster.setId(dto.monster_id());
        monster.setMongoId(dto._id());

        if (dto.monster_info() != null && !dto.monster_info().isEmpty()) {
            String nome = dto.monster_info();
            monster.setName(nome.substring(0, 1).toUpperCase() + nome.substring(1));
        } else {
            monster.setName("Unknown");
        }

        monster.setSize(dto.size());
        monster.setRace(dto.race());
        monster.setType(dto.type());
        monster.setElementPower(dto.element_power());
        monster.setGifUrl(dto.gif());

        monster.setStats(mapStats(dto.main_stats()));
        monster.setAttributes(mapAttributes(dto.main_atb()));
        monster.setElementalDamage(mapElementalDamage(dto.elementalDamage()));

        return monster;
    }

    // 2. Entity (Banco) -> Domain (Para Batalha)
    public Monster toDomain(MonsterEntity entity) {
        if (entity == null) return null;

        Monster monster = new Monster();
        monster.setId(entity.getId());
        monster.setName(entity.getName());
        monster.setGifUrl(entity.getGifUrl());

        MainStats stats = new MainStats();
        stats.setHp(entity.getHp() != null ? entity.getHp() : 0);
        stats.setAttack(entity.getAttack() != null ? entity.getAttack() : 0);
        stats.setDef(entity.getDef() != null ? entity.getDef() : 0);
        stats.setM_def(entity.getMDef() != null ? entity.getMDef() : 0);
        monster.setStats(stats);

        // Mapeamento de Drops (Com a correção)
        if (entity.getDrops() != null) {
            List<MonsterDrop> drops = entity.getDrops().stream()
                    .map(this::mapDrop)
                    .collect(Collectors.toList());
            monster.setDrops(drops);
        } else {
            monster.setDrops(new ArrayList<>());
        }

        return monster;
    }

    // Helper: DropEntity -> DropDomain
    private MonsterDrop mapDrop(MonsterDropEntity entity) {
        MonsterDrop drop = new MonsterDrop();
        drop.setRate(entity.getRate());

        // Aqui usamos o ItemMapper para converter ItemEntity -> Item
        if (entity.getItem() != null) {
            drop.setItem(itemMapper.toDomain(entity.getItem()));
        }
        return drop;
    }

    // 3. Domain -> Entity (Para Salvar)
    public MonsterEntity toEntity(Monster domain) {
        if (domain == null) return null;

        MonsterEntity entity = new MonsterEntity();
        entity.setId(domain.getId());
        entity.setMongoId(domain.getMongoId());
        entity.setName(domain.getName());
        entity.setSize(domain.getSize());
        entity.setRace(domain.getRace());
        entity.setType(domain.getType());
        entity.setElementPower(domain.getElementPower());
        entity.setGifUrl(domain.getGifUrl());

        if (domain.getStats() != null) {
            entity.setHp(domain.getStats().getHp());
            entity.setLevel(domain.getStats().getLevel());
            entity.setDef(domain.getStats().getDef());
            entity.setMDef(domain.getStats().getM_def());
            entity.setAttack(domain.getStats().getAttack());
            entity.setMagicAttack(domain.getStats().getMagic_attack());
            entity.setAspd(domain.getStats().getAspd());
            entity.setMoveSpeed(domain.getStats().getMove_speed());
            entity.setBaseExp(domain.getStats().getBase_exp());
            entity.setJobExp(domain.getStats().getJob_exp());
            entity.setFlee(domain.getStats().getFlee());
            entity.setHit(domain.getStats().getHit());
            entity.setDefenseRating(domain.getStats().getDefense_rating());
            entity.setCritShield(domain.getStats().getCrit_shield());
            entity.setExpRatio(domain.getStats().getExp_ratio());
        }

        if (domain.getAttributes() != null) {
            entity.setAgi(domain.getAttributes().getAgi());
            entity.setIntelligence(domain.getAttributes().getIntelligence());
            entity.setLuk(domain.getAttributes().getLuk());
            entity.setVit(domain.getAttributes().getVit());
            entity.setDex(domain.getAttributes().getDex());
        }

        if (domain.getElementalDamage() != null) {
            entity.setElementFire(domain.getElementalDamage().getFire());
            entity.setElementWater(domain.getElementalDamage().getWater());
            entity.setElementEarth(domain.getElementalDamage().getEarth());
            entity.setElementWind(domain.getElementalDamage().getWind());
            entity.setElementNeutral(domain.getElementalDamage().getNeutral());
            entity.setElementHoly(domain.getElementalDamage().getHoly());
            entity.setElementShadow(domain.getElementalDamage().getShadow());
            entity.setElementGhost(domain.getElementalDamage().getGhost());
            entity.setElementUndead(domain.getElementalDamage().getUndead());
            entity.setElementPoison(domain.getElementalDamage().getPoison());
        }

        return entity;
    }

    // --- Helpers Privados (Stats) ---

    private MainStats mapStats(MonsterDTO.MainStatsDTO dto) {
        if (dto == null) return null;
        MainStats stats = new MainStats();
        stats.setHp(parseStat(dto.hp()));
        stats.setLevel(parseStat(dto.level()));
        stats.setDef(parseStat(dto.def()));
        stats.setM_def(parseStat(dto.m_def()));
        stats.setAttack(parseStat(dto.attack()));
        stats.setMagic_attack(parseStat(dto.magic_attack()));
        stats.setAspd(parseStat(dto.aspd()));
        stats.setMove_speed(parseStat(dto.move_speed()));
        stats.setBase_exp(parseStat(dto.base_exp()));
        stats.setJob_exp(parseStat(dto.job_exp()));
        stats.setFlee(parseStat(dto.flee()));
        stats.setHit(parseStat(dto.hit()));
        stats.setDefense_rating(parseStat(dto.defense_rating()));
        stats.setCrit_shield(parseStat(dto.crit_shield()));
        stats.setExp_ratio(parseStat(dto.exp_ratio()));
        return stats;
    }

    private MainAttributes mapAttributes(MonsterDTO.MainAtbDTO dto) {
        if (dto == null) return null;
        MainAttributes attr = new MainAttributes();
        attr.setAgi(dto.agi());
        attr.setIntelligence(dto.int_val());
        attr.setLuk(dto.luk());
        attr.setVit(dto.vit());
        attr.setDex(dto.dex());
        return attr;
    }

    private ElementalDamage mapElementalDamage(MonsterDTO.ElementalDamageDTO dto) {
        if (dto == null) return null;
        ElementalDamage elm = new ElementalDamage();
        elm.setNeutral(dto.neutral());
        elm.setPoison(dto.poison());
        elm.setEarth(dto.earth());
        elm.setShadow(dto.shadow());
        elm.setWater(dto.water());
        elm.setUndead(dto.undead());
        elm.setFire(dto.fire());
        elm.setHoly(dto.holy());
        elm.setWind(dto.wind());
        elm.setGhost(dto.ghost());
        return elm;
    }

    private Integer parseStat(String val) {
        try {
            if (val == null) return 0;
            String clean = val.replace(",", "");
            if (clean.contains("~")) clean = clean.split("~")[0];
            if (clean.contains("+")) clean = clean.split("\\+")[0];
            clean = clean.trim();
            return (int) Double.parseDouble(clean);
        } catch (Exception e) {
            return 0;
        }
    }
}