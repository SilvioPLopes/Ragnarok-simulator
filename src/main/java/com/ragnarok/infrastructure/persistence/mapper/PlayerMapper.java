package com.ragnarok.infrastructure.persistence.mapper;

import com.ragnarok.domain.model.*;
import com.ragnarok.infrastructure.client.mapper.ItemMapper;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlayerMapper {

    private final ItemMapper itemMapper;

    public PlayerMapper(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    // 1. Entity -> Domain
    public Player toDomain(PlayerEntity entity) {
        if (entity == null) return null;

        Player player = new Player();
        player.setId(entity.getId());
        player.setName(entity.getName());
        player.setJobClass(entity.getJobClass() != null ? entity.getJobClass().toUpperCase() : null);
        player.setGender(entity.getGender());

        player.setBaseLevel(entity.getBaseLevel() != null ? entity.getBaseLevel() : 1);
        player.setJobLevel(entity.getJobLevel() != null ? entity.getJobLevel() : 1);
        player.setBaseExp(entity.getBaseExp() != null ? entity.getBaseExp() : 0L);
        player.setJobExp(entity.getJobExp() != null ? entity.getJobExp() : 0L);
        player.setZenny(entity.getZenny() != null ? entity.getZenny() : 0L);

        player.setHpCurrent(entity.getHpCurrent() != null ? entity.getHpCurrent() : 0);
        player.setSpCurrent(entity.getSpCurrent() != null ? entity.getSpCurrent() : 0);

        // Stats
        PlayerStats stats = new PlayerStats();
        stats.setStr(entity.getStr() != null ? entity.getStr() : 1);
        stats.setAgi(entity.getAgi() != null ? entity.getAgi() : 1);
        stats.setVit(entity.getVit() != null ? entity.getVit() : 1);
        stats.setIntVal(entity.getIntelligence() != null ? entity.getIntelligence() : 1);
        stats.setDex(entity.getDex() != null ? entity.getDex() : 1);
        stats.setLuk(entity.getLuk() != null ? entity.getLuk() : 1);
        stats.setMaxHp(entity.getHpMax() != null ? entity.getHpMax() : 100);
        stats.setMaxSp(entity.getSpMax() != null ? entity.getSpMax() : 40);
        player.setStats(stats);



        // Location (Reconstrução)
        if (entity.getMapName() != null) {
            PlayerLocation loc = new PlayerLocation();
            loc.setMapName(entity.getMapName() != null ? entity.getMapName() : "Prontera");
            loc.setX(entity.getCoordX() != null ? entity.getCoordX().doubleValue() : 0.0);
            loc.setY(entity.getCoordY() != null ? entity.getCoordY().doubleValue() : 0.0);
            player.setLocation(loc);
        }

        // Inventário
        if (entity.getInventory() != null) {
            List<PlayerItem> domainInventory = entity.getInventory().stream()
                    .map(this::toDomainItem)
                    .collect(Collectors.toList());
            player.setInventory(domainInventory);
        } else {
            player.setInventory(new ArrayList<>());
        }

        return player;
    }

    // 2. Domain -> Entity (Completo para salvar tudo)
    public PlayerEntity toEntity(Player domain) {
        if (domain == null) return null;

        PlayerEntity entity = new PlayerEntity();

        // Campos Básicos
        entity.setName(domain.getName());
        entity.setJobClass(domain.getJobClass());
        entity.setBaseLevel(domain.getBaseLevel());
        entity.setJobLevel(domain.getJobLevel());
        entity.setHpCurrent(domain.getHpCurrent());
        entity.setSpCurrent(domain.getSpCurrent());
        entity.setBaseExp(domain.getBaseExp());
        entity.setJobExp(domain.getJobExp());
        entity.setZenny(domain.getZenny());
        entity.setStatPoints(domain.getStatPoints());
        entity.setSkillPoints(domain.getSkillPoints());

        // Flattening Stats
        if (domain.getStats() != null) {
            entity.setStr(domain.getStats().getStr());
            entity.setAgi(domain.getStats().getAgi());
            entity.setVit(domain.getStats().getVit());
            entity.setIntelligence(domain.getStats().getIntVal());
            entity.setDex(domain.getStats().getDex());
            entity.setLuk(domain.getStats().getLuk());
            entity.setHpMax(domain.getStats().getMaxHp());
            entity.setSpMax(domain.getStats().getMaxSp());
        }

        // Flattening Location
        if (domain.getLocation() != null) {
            entity.setMapName(domain.getLocation().getMapName());
            if (domain.getLocation().getX() != null)
                entity.setCoordX(domain.getLocation().getX().intValue());
            if (domain.getLocation().getY() != null)
                entity.setCoordY(domain.getLocation().getY().intValue());
        }

        return entity;
    }

    private PlayerItem toDomainItem(PlayerItemEntity entity) {
        PlayerItem domain = new PlayerItem();
        domain.setId(entity.getId());
        domain.setAmount(entity.getAmount());
        domain.setRefineLevel(entity.getRefineLevel());
        domain.setIsEquipped(entity.getEquipped());

        if (entity.getItem() != null) {
            domain.setItemDefinition(itemMapper.toDomain(entity.getItem()));
        }

        return domain;
    }
}