package com.ragnarok.infrastructure.client.mapper;

import com.ragnarok.domain.model.Monster;
import com.ragnarok.domain.model.MonsterDrop;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.MonsterDropEntity;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica o mapeamento de rate de MonsterDropEntity → MonsterDrop.
 *
 * Contrato do rate:
 *   - No banco (coluna DOUBLE PRECISION): armazenado em escala 0–100 (já normalizado).
 *     A normalização rAthena (0–10000 → ÷100) ocorre na ingestão pelo monster_drops.sql.
 *   - No domínio (MonsterDrop.rate): mesma escala 0–100.
 *   - No BattleEngine: roll = nextDouble(0, 100), drop ocorre se roll < rate.
 *
 *   Portanto o mapper NÃO divide — repassa o valor diretamente do banco.
 */
class MonsterMapperTest {

    private MonsterMapper monsterMapper;

    @BeforeEach
    void setUp() {
        monsterMapper = new MonsterMapper(new ItemMapper());
    }

    // ── Passagem direta de rate do banco → domínio ────────────────────────────

    @Test
    @DisplayName("Rate 100.0 no banco (100%) → domain rate 100.0")
    void mapDrop_rate100_repassado() {
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(100.0));

        assertEquals(100.0, monster.getDrops().get(0).getRate(), 0.001,
                "rate=100.0 no banco deve chegar como 100.0 no domain (100% de drop)");
    }

    @Test
    @DisplayName("Rate 70.0 no banco (70%) → domain rate 70.0")
    void mapDrop_rate70_repassado() {
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(70.0));

        assertEquals(70.0, monster.getDrops().get(0).getRate(), 0.001,
                "rate=70.0 deve chegar intacto no domínio");
    }

    @Test
    @DisplayName("Rate 0.35 no banco (0.35%) → domain rate 0.35")
    void mapDrop_rate0_35_repassado() {
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(0.35));

        assertEquals(0.35, monster.getDrops().get(0).getRate(), 0.001,
                "rate=0.35 (0.35%, ex-rAthena 35 ÷ 100) deve chegar intacto");
    }

    @Test
    @DisplayName("Rate 0.0 no banco → domain rate 0.0 (nunca dropa)")
    void mapDrop_rate0_repassado() {
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(0.0));

        assertEquals(0.0, monster.getDrops().get(0).getRate(), 0.001,
                "rate=0.0 deve chegar como 0.0 — item nunca dropa");
    }

    @Test
    @DisplayName("Rate null no entity → domain rate 0.0 (nunca dropa)")
    void mapDrop_rateNull_viraZero() {
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(null));

        assertEquals(0.0, monster.getDrops().get(0).getRate(), 0.001,
                "rate null deve ser tratado como 0.0 para evitar NullPointerException no BattleEngine");
    }

    @Test
    @DisplayName("Drop item não nulo é mapeado corretamente para o domínio")
    void mapDrop_itemNaoNulo_mapeadoCorretamente() {
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(50.0));

        MonsterDrop drop = monster.getDrops().get(0);
        assertNotNull(drop.getItem(), "item do drop não pode ser null no domínio");
        assertEquals("Item Teste", drop.getItem().getName());
    }

    @Test
    @DisplayName("Rate no domínio nunca deve ultrapassar 100.0 (invariante de escala)")
    void mapDrop_rateSempre0a100NoDomain() {
        // A ingestão SQL garante a normalização — rates vindo do banco já estão em 0-100.
        // Este teste documenta o invariante: qualquer rate > 100 no banco indica dado corrompido.
        Monster monster = monsterMapper.toDomain(buildEntityComDrop(100.0));

        assertTrue(monster.getDrops().get(0).getRate() <= 100.0,
                "Rate no domínio deve estar em 0-100 — valor acima indica dado não normalizado no banco");
    }

    @Test
    @DisplayName("Monstro sem drops retorna lista vazia no domain")
    void toDomain_semDrops_retornaListaVazia() {
        MonsterEntity entity = buildMonsterEntity();
        entity.setDrops(new ArrayList<>());

        Monster monster = monsterMapper.toDomain(entity);

        assertNotNull(monster.getDrops());
        assertTrue(monster.getDrops().isEmpty());
    }

    @Test
    @DisplayName("Monstro com drops null retorna lista vazia no domain")
    void toDomain_dropsNull_retornaListaVazia() {
        MonsterEntity entity = buildMonsterEntity();
        entity.setDrops(null);

        Monster monster = monsterMapper.toDomain(entity);

        assertNotNull(monster.getDrops());
        assertTrue(monster.getDrops().isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MonsterEntity buildEntityComDrop(Double rate) {
        MonsterEntity entity = buildMonsterEntity();

        ItemEntity item = new ItemEntity();
        item.setId(999L);
        item.setName("Item Teste");

        MonsterDropEntity dropEntity = new MonsterDropEntity();
        dropEntity.setMonster(entity);
        dropEntity.setItem(item);
        dropEntity.setRate(rate);

        entity.setDrops(List.of(dropEntity));
        return entity;
    }

    private MonsterEntity buildMonsterEntity() {
        MonsterEntity entity = new MonsterEntity();
        entity.setId(1002L);
        entity.setName("Poring");
        entity.setHp(50);
        return entity;
    }
}
