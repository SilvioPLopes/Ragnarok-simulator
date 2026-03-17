package com.ragnarok.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.domain.model.ActiveBuff;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BuffSerializer {

    private final ObjectMapper objectMapper;

    public BuffSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ActiveBuff> fromJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ActiveBuff>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public String toJson(List<ActiveBuff> buffs) {
        if (buffs == null || buffs.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(buffs);
        } catch (Exception e) {
            return null;
        }
    }
}
