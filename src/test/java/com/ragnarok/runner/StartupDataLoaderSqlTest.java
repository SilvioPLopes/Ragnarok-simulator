package com.ragnarok.runner;

import com.ragnarok.AbstractIntegrationTest;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa que os scripts SQL de carga em massa (monster_drops.sql, map_monsters.sql)
 * executam sem violação de FK e filtram corretamente referências inválidas.
 *
 * Cenário coberto: o perfil "test" pula esses scripts no StartupDataLoader,
 * portanto sem esse teste o fix do WHERE EXISTS nunca seria exercido automaticamente.
 */
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=update"})
class StartupDataLoaderSqlTest extends AbstractIntegrationTest {

    // ID real do rAthena — presente em monster_drops.sql e map_monsters.sql
    private static final long PORING_ID = 1002L;
    // Primeiro item que Poring dropa em monster_drops.sql: (1002, 909, 7000)
    private static final long ITEM_909_ID = 909L;
    // Monster de evento — presente nos SQLs mas ausente na tabela monsters
    private static final long EVENT_MONSTER_ID = 20649L;

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MonsterRepository monsterRepository;
    @Autowired private ItemRepository itemRepository;

    @BeforeEach
    void setup() {
        limpar();

        MonsterEntity poring = new MonsterEntity();
        poring.setId(PORING_ID);
        poring.setName("Poring");
        poring.setHp(50);
        monsterRepository.save(poring);

        ItemEntity item = new ItemEntity();
        item.setId(ITEM_909_ID);
        item.setName("Ghost Bandana");
        item.setType(ItemType.ETC);
        item.setWeight(1);
        itemRepository.save(item);
    }

    @AfterEach
    void teardown() {
        limpar();
    }

    // ── monster_drops.sql ────────────────────────────────────────────────────

    @Test
    @DisplayName("monster_drops.sql: executa sem FK violation com banco parcialmente populado")
    void monsterDropsSql_naoLancaFkViolation() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertDoesNotThrow(
                () -> ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/monster_drops.sql")),
                "monster_drops.sql lançou exceção — provavelmente FK violation sem o WHERE EXISTS"
            );
        }
    }

    @Test
    @DisplayName("monster_drops.sql: insere drop quando monster E item existem")
    void monsterDropsSql_insereDropQuandoReferenciaValida() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/monster_drops.sql"));
        }

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM monster_drops WHERE monster_id = ? AND item_id = ?",
            Long.class, PORING_ID, ITEM_909_ID
        );
        assertEquals(1L, count,
            "Drop (monster=1002, item=909) deve ser inserido — ambas as referências existem");
    }

    @Test
    @DisplayName("monster_drops.sql: filtra drops cujo item não existe na tabela items")
    void monsterDropsSql_filtraDropsComItemInexistente() throws Exception {
        // Item 1202 (segundo drop do Poring) NÃO foi inserido no setup
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/monster_drops.sql"));
        }

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM monster_drops WHERE monster_id = ? AND item_id = 1202",
            Long.class, PORING_ID
        );
        assertEquals(0L, count,
            "Drop com item_id=1202 deve ser filtrado pois o item não existe em items");
    }

    @Test
    @DisplayName("monster_drops.sql: filtra todos os drops de monster_id inexistente")
    void monsterDropsSql_filtraTodosDropsDeMonsterInexistente() throws Exception {
        // EVENT_MONSTER_ID (20649) não está em monsters — nenhum drop deve ser inserido
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/monster_drops.sql"));
        }

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM monster_drops WHERE monster_id = ?",
            Long.class, EVENT_MONSTER_ID
        );
        assertEquals(0L, count,
            "Monster 20649 (evento) não existe em monsters — todos seus drops devem ser filtrados");
    }

    @Test
    @DisplayName("monster_drops.sql: idempotente — segunda execução não duplica registros")
    void monsterDropsSql_idempotente() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/monster_drops.sql"));
        }
        Long countApos1a = jdbc.queryForObject("SELECT COUNT(*) FROM monster_drops", Long.class);

        try (Connection conn = dataSource.getConnection()) {
            assertDoesNotThrow(
                () -> ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/monster_drops.sql")),
                "Segunda execução de monster_drops.sql não deve lançar exceção"
            );
        }
        Long countApos2a = jdbc.queryForObject("SELECT COUNT(*) FROM monster_drops", Long.class);

        assertEquals(countApos1a, countApos2a,
            "Segunda execução não deve alterar a contagem — ON CONFLICT DO UPDATE é idempotente");
    }

    // ── map_monsters.sql ─────────────────────────────────────────────────────

    @Test
    @DisplayName("map_monsters.sql: executa sem FK violation com banco parcialmente populado")
    void mapMonstersSql_naoLancaFkViolation() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertDoesNotThrow(
                () -> ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/map_monsters.sql")),
                "map_monsters.sql lançou exceção — provavelmente FK violation sem o WHERE EXISTS"
            );
        }
    }

    @Test
    @DisplayName("map_monsters.sql: insere spawns quando monster existe")
    void mapMonstersSql_insereSpawnQuandoMonsterValido() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/map_monsters.sql"));
        }

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM map_monsters WHERE monster_id = ?",
            Long.class, PORING_ID
        );
        assertTrue(count > 0,
            "Poring (1002) existe em monsters — deve ter ao menos 1 spawn entry em map_monsters");
    }

    @Test
    @DisplayName("map_monsters.sql: filtra spawns de monster_id inexistente (evento/custom)")
    void mapMonstersSql_filtraSpawnDeMonsterInexistente() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/map_monsters.sql"));
        }

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM map_monsters WHERE monster_id = ?",
            Long.class, EVENT_MONSTER_ID
        );
        assertEquals(0L, count,
            "Monster 20649 (evento) não existe — seus spawns devem ser filtrados pelo WHERE EXISTS");
    }

    @Test
    @DisplayName("map_monsters.sql: idempotente — segunda execução não duplica registros")
    void mapMonstersSql_idempotente() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/map_monsters.sql"));
        }
        Long countApos1a = jdbc.queryForObject("SELECT COUNT(*) FROM map_monsters", Long.class);

        try (Connection conn = dataSource.getConnection()) {
            assertDoesNotThrow(
                () -> ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/map_monsters.sql")),
                "Segunda execução de map_monsters.sql não deve lançar exceção"
            );
        }
        Long countApos2a = jdbc.queryForObject("SELECT COUNT(*) FROM map_monsters", Long.class);

        assertEquals(countApos1a, countApos2a,
            "Segunda execução não deve alterar a contagem — ON CONFLICT DO UPDATE é idempotente");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void limpar() {
        jdbc.update("DELETE FROM monster_drops WHERE monster_id IN (?, ?)", PORING_ID, EVENT_MONSTER_ID);
        jdbc.update("DELETE FROM map_monsters  WHERE monster_id IN (?, ?)", PORING_ID, EVENT_MONSTER_ID);
        try {
            jdbc.update("DELETE FROM monster_spawns WHERE monster_id IN (?, ?)", PORING_ID, EVENT_MONSTER_ID);
        } catch (Exception ignored) { /* table may not exist in all environments */ }
        jdbc.update("DELETE FROM monsters WHERE id IN (?, ?)", PORING_ID, EVENT_MONSTER_ID);
        jdbc.update("DELETE FROM monster_drops WHERE item_id = ?", ITEM_909_ID);
        jdbc.update("DELETE FROM items WHERE id = ?", ITEM_909_ID);
    }
}
