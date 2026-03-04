package com.ragnarok.runner.importer;

import com.ragnarok.domain.model.EquipSlot;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ItemDbParser {

    public List<ItemEntity> parse(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(yamlContent);

        List<Map<String, Object>> body = (List<Map<String, Object>>) root.get("Body");
        if (body == null) return Collections.emptyList();

        return body.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ItemEntity toEntity(Map<String, Object> item) {
        try {
            ItemEntity entity = new ItemEntity();

            entity.setId(toLong(item.get("Id")));
            entity.setName((String) item.get("Name"));
            entity.setDescription((String) item.get("Name")); // rAthena não tem descrição, usa o nome

            // Tipo do item (Weapon, Armor, Healing, etc.)
            entity.setType(mapType((String) item.get("Type")));

            // Stats básicos
            entity.setAttack(toInt(item.get("Attack")));
            entity.setMagicAttack(toInt(item.get("MagicAttack")));
            entity.setDefense(toInt(item.get("Defense")));
            entity.setRange(toInt(item.get("Range")));
            entity.setSlots(toInt(item.get("Slots")));
            entity.setLevelMin(toInt(item.get("EquipLevelMin")));
            entity.setWeight(toInt(item.get("Weight")));
            entity.setPrice(toInt(item.get("Buy")));

            // EquipSlot — vem como um mapa de localizations (ex: {Head_Top: true})
            Object locations = item.get("Locations");
            if (locations instanceof Map) {
                entity.setEquipSlot(mapLocation((Map<String, Object>) locations));
            } else {
                entity.setEquipSlot(EquipSlot.NONE);
            }

            return entity;
        } catch (Exception e) {
            System.err.println("Erro ao parsear item: " + item.get("Name") + " — " + e.getMessage());
            return null;
        }
    }

    // Traduz o tipo do rAthena para o nosso enum ItemType
    private ItemType mapType(String rathenaType) {
        if (rathenaType == null) return ItemType.ETC;
        return switch (rathenaType) {
            case "Weapon"                              -> ItemType.WEAPON;
            case "Armor"                               -> ItemType.ARMOR;
            case "Healing", "Delayconsume",
                 "Usable", "Cash"                     -> ItemType.CONSUMABLE;
            case "Ammo"                                -> ItemType.AMMO;
            case "Card"                                -> ItemType.CARD;
            default                                    -> ItemType.ETC;
        };
    }

    // Traduz as Locations do rAthena para o nosso enum EquipSlot
    private EquipSlot mapLocation(Map<String, Object> locations) {
        if (locations == null) return EquipSlot.NONE;
        if (locations.containsKey("Head_Top"))         return EquipSlot.HEAD_UPPER;
        if (locations.containsKey("Head_Mid"))         return EquipSlot.HEAD_MIDDLE;
        if (locations.containsKey("Head_Low"))         return EquipSlot.HEAD_LOWER;
        if (locations.containsKey("Armor"))            return EquipSlot.ARMOR;
        if (locations.containsKey("Right_Hand"))       return EquipSlot.HAND_R;
        if (locations.containsKey("Left_Hand"))        return EquipSlot.HAND_L;
        if (locations.containsKey("Both_Hand"))        return EquipSlot.HAND_2H;
        if (locations.containsKey("Garment"))          return EquipSlot.GARMENT;
        if (locations.containsKey("Shoes"))            return EquipSlot.BOOTS;
        if (locations.containsKey("Right_Accessory"))  return EquipSlot.ACCESSORY_RIGHT;
        if (locations.containsKey("Left_Accessory"))   return EquipSlot.ACCESSORY_LEFT;
        return EquipSlot.NONE;
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