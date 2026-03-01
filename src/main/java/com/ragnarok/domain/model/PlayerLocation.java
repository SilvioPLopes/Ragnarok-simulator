package com.ragnarok.domain.model;

import lombok.*;

// Sem @Embeddable.
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlayerLocation {
    private String mapName;
    private Double x;
    private Double y;
    private String savePointMap;
}