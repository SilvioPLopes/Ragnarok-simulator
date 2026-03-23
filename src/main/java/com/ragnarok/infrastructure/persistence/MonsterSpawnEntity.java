package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

/**
 * @deprecated Substituído por {@link MapMonsterEntity} que usa dados reais do rAthena.
 * Mantido apenas para compatibilidade com o teste {@code MapSpawnIntegrationTest}.
 */
@Deprecated
@Entity
@Table(name = "monster_spawns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MonsterSpawnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id")
    private MonsterEntity monster;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "map_id") // Linka com o String ID ("moc_fild08")
    private GameMapEntity map;

    private Integer amount;      // "80"
    private String respawnTime;  // "instantly" ou "10 min"
}