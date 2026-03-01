package com.ragnarok.infrastructure.client.mapper;

import com.ragnarok.domain.model.Item;
import com.ragnarok.domain.model.ItemStats;
import com.ragnarok.infrastructure.client.dto.ItemDTO;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import org.springframework.stereotype.Component;
import java.util.ArrayList;

@Component
public class ItemMapper {

    // 1. API (DTO) -> Domain
    public Item toDomain(ItemDTO dto) {
        if (dto == null) return null;

        Item item = new Item();
        item.setId(dto.id());
        item.setMongoId(dto._id());
        item.setName(dto.name());
        item.setDescription(dto.description());
        item.setImgUrl(dto.img());

        // Mapeia Stats se existirem
        if (dto.equipable() != null) {
            ItemStats stats = new ItemStats();
            stats.setAttack(dto.equipable().attack());
            stats.setDefense(dto.equipable().defense());
            stats.setRange(dto.equipable().range());
            stats.setSlots(dto.equipable().slots());
            stats.setLevelMin(dto.equipable().level_min());
            item.setStats(stats);
        }

        // TODO: Mapear drops quando tivermos a entidade pronta
        item.setDroppedBy(new ArrayList<>());

        return item;
    }

    // 2. Domain -> Entity (Banco)
    public ItemEntity toEntity(Item domain) {
        if (domain == null) return null;

        ItemEntity entity = new ItemEntity();
        entity.setId(domain.getId());
        entity.setMongoId(domain.getMongoId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setImgUrl(domain.getImgUrl());

        // Flattening: Domain Object -> Entity Columns
        if (domain.getStats() != null) {
            entity.setAttack(domain.getStats().getAttack());
            entity.setDefense(domain.getStats().getDefense());
            entity.setRange(domain.getStats().getRange());
            entity.setSlots(domain.getStats().getSlots());
            entity.setLevelMin(domain.getStats().getLevelMin());
        }

        return entity;
    }

    public Item toDomain(ItemEntity entity) {
        if (entity == null) return null;

        Item item = new Item();
        item.setId(entity.getId());
        item.setMongoId(entity.getMongoId());
        item.setName(entity.getName());
        item.setDescription(entity.getDescription());
        item.setImgUrl(entity.getImgUrl());

        ItemStats stats = new ItemStats();
        stats.setAttack(entity.getAttack());
        stats.setDefense(entity.getDefense());
        stats.setRange(entity.getRange());
        stats.setSlots(entity.getSlots());
        stats.setLevelMin(entity.getLevelMin());

        // CORREÇÃO: Removido stats.setEquipSlot() pois ItemStats não tem esse campo.
        // Se precisar do slot, adicione o campo 'EquipSlot' direto na classe Item (Domain), não em Stats.

        item.setStats(stats);
        item.setDroppedBy(new ArrayList<>());

        return item;
    }
}