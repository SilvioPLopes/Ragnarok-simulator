package com.ragnarok.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;

/**
 * Popula automaticamente as tabelas de dados estáticos na inicialização.
 * Roda depois do RathenaImporter (@Order 1) e do player init (@Order 2).
 * Cada tabela é populada apenas se estiver vazia — idempotente.
 * Em perfil "test", dados volumosos (maps, portals, spawns, drops) são pulados.
 */
@Component
@Order(3)
public class StartupDataLoader implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Environment environment;

    public StartupDataLoader(JdbcTemplate jdbc, DataSource dataSource, Environment environment) {
        this.jdbc        = jdbc;
        this.dataSource  = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureUniqueConstraints();
        forceLoad("db/skills.sql");                                        // ON CONFLICT DO UPDATE — seguro sempre
        forceLoad("db/skill_effects.sql");                                 // ALTER + UPDATE idempotente
        forceLoad("db/weapon_size_modifiers.sql");                         // ON CONFLICT DO UPDATE — idempotente
        loadIfEmpty("skill_tree",    "db/skill_tree.sql",      0);

        // Dados volumosos — pulados no perfil "test" para evitar lentidão
        boolean isTest = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!isTest) {
            loadIfEmpty("maps",          "db/maps.sql",            0);
            loadIfEmpty("map_portals",   "db/map_portals_v2.sql",  0);
            loadIfEmpty("map_monsters",  "db/map_monsters.sql",    0);
            loadIfEmpty("monster_drops", "db/monster_drops.sql",   0);
        }
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

        // Deduplica antes de criar constraint — forceLoad anterior pode ter inserido duplicatas
        // se a constraint ainda não existia (ex: primeiro boot ou banco de testes limpo)
        deduplicarPorCtid("skill_buff_effects", "skill_id, stat_type");
        addUniqueConstraint("uq_skill_buff_effects_skill_stat",
                "ALTER TABLE skill_buff_effects ADD CONSTRAINT uq_skill_buff_effects_skill_stat UNIQUE (skill_id, stat_type)");
    }

    /**
     * Remove linhas duplicadas de uma tabela usando ctid (identificador físico de tupla do PostgreSQL).
     * Não depende de nenhuma coluna específica além das colunas de unicidade.
     */
    private void deduplicarPorCtid(String tabela, String colunas) {
        try {
            int removidas = jdbc.update(
                "DELETE FROM " + tabela + " WHERE ctid NOT IN (" +
                "  SELECT min(ctid) FROM " + tabela + " GROUP BY " + colunas + ")");
            if (removidas > 0) {
                System.out.printf("🧹 Removidas %d duplicatas de %s (%s)%n", removidas, tabela, colunas);
            }
        } catch (Exception e) {
            System.err.printf("⚠️ Erro ao deduplicar %s: %s%n", tabela, e.getMessage());
        }
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
