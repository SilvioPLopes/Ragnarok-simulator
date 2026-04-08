package com.ragnarok.api.dto.response;

import java.util.List;

public record MapInfoResponseDTO(String currentMap, String displayName, List<String> availablePortals) {}
