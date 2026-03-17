package com.ragnarok.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Popula automaticamente as tabelas de dados estáticos na inicialização.
 * Roda depois do RathenaImporter (@Order 1) e do player init (@Order 2).
 * Cada tabela é populada apenas se estiver vazia — idempotente.
 */
@Component
@Order(3)
public class StartupDataLoader implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public StartupDataLoader(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc       = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureUniqueConstraints();
        forceLoad("db/skills.sql");                                        // ON CONFLICT DO UPDATE — seguro sempre
        loadIfEmpty("maps",          "db/maps.sql",            0);
        loadIfEmpty("map_portals",   "db/map_portals_v2.sql",  0);
        loadIfEmpty("map_monsters",  "db/map_monsters.sql",    0);
        loadIfEmpty("monster_drops", "db/monster_drops.sql",   0);
        loadIfEmpty("skill_tree",    "db/skill_tree.sql",      0);
    }

    /**
     * Garante que as UNIQUE constraints existam — necessárias para os ON CONFLICT
     * dos SQLs. O JPA cria as tabelas sem elas.
     * Usa DO $$ para compatibilidade com PostgreSQL < 16 (sem ADD CONSTRAINT IF NOT EXISTS).
     */
    private void ensureUniqueConstraints() {
        addUniqueConstraint("uq_map_monsters_map_monster",
                "ALTER TABLE map_monsters ADD CONSTRAINT uq_map_monsters_map_monster UNIQUE (map_id, monster_id)");
        addUniqueConstraint("uq_monster_drops_monster_item",
                "ALTER TABLE monster_drops ADD CONSTRAINT uq_monster_drops_monster_item UNIQUE (monster_id, item_id)");
        addUniqueConstraint("uq_skill_tree_class_skill_prereq",
                "ALTER TABLE skill_tree ADD CONSTRAINT uq_skill_tree_class_skill_prereq UNIQUE (job_class, skill_id, prereq_skill)");
    }

    private void addUniqueConstraint(String constraintName, String alterSql) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ?)",
                Boolean.class, constraintName);
        if (Boolean.FALSE.equals(exists)) {
            jdbc.execute(alterSql);
            System.out.println("✅ Constraint criada: " + constraintName);
        }
    }

    /**
     * Executa o SQL sem verificar se a tabela está vazia.
     * Usado para tabelas com ON CONFLICT DO UPDATE — idempotente por design.
     */
    private void forceLoad(String sqlFile) {
        System.out.printf("🔄 Atualizando via force-load: %s...%n", sqlFile);
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource(sqlFile));
            System.out.printf("✅ %-20s atualizada (force).%n", sqlFile);
        } catch (Exception e) {
            System.err.printf("❌ Erro ao carregar %s: %s%n", sqlFile, e.getMessage());
        }
    }

    private void loadIfEmpty(String tableName, String sqlFile, long threshold) {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        if (count > threshold) {
            System.out.printf("📦 %-15s já populada (%d registros). Pulando.%n", tableName, count);
            return;
        }

        System.out.printf("🔄 Populando %-15s a partir de %s...%n", tableName, sqlFile);
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource(sqlFile));
            long after = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            System.out.printf("✅ %-15s populada com %d registros.%n", tableName, after);
        } catch (Exception e) {
            System.err.printf("❌ Erro ao popular %s: %s%n", tableName, e.getMessage());
        }
    }
}
