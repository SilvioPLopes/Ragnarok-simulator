package com.ragnarok.runner.importer;

import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
@Profile("!test")
public class RathenaImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RathenaImporter.class);

    private static final int MONSTER_MIN_EXPECTED = 2600;
    private static final int ITEM_MIN_EXPECTED    = 25_000;

    private final MonsterRepository monsterRepo;
    private final ItemRepository itemRepo;
    private final MobDbParser mobParser;
    private final ItemDbParser itemParser;

    public RathenaImporter(MonsterRepository monsterRepo,
                           ItemRepository itemRepo,
                           MobDbParser mobParser,
                           ItemDbParser itemParser) {
        this.monsterRepo = monsterRepo;
        this.itemRepo    = itemRepo;
        this.mobParser   = mobParser;
        this.itemParser  = itemParser;
    }

    @Override
    public void run(String... args) {
        importMonsters();
        importItems();
    }

    private void importMonsters() {
        long count = monsterRepo.count();
        if (count >= MONSTER_MIN_EXPECTED) {
            log.info("Monsters already complete ({} in DB). Skipping import.", count);
            return;
        }
        log.info("Importing monsters from classpath (current: {})...", count);
        List<MonsterEntity> monsters = mobParser.parse(readClasspath("rathena/mob_db.yml"));
        monsterRepo.saveAll(monsters);
        log.info("Imported {} monsters.", monsters.size());
    }

    private void importItems() {
        long count = itemRepo.count();
        if (count >= ITEM_MIN_EXPECTED) {
            log.info("Items already complete ({} in DB). Skipping import.", count);
            return;
        }
        log.info("Importing items from classpath (current: {})...", count);
        List<ItemEntity> todos = new ArrayList<>();
        todos.addAll(parseItem("item_db_usable.yml", "Usable"));
        todos.addAll(parseItem("item_db_equip.yml",  "Equip"));
        todos.addAll(parseItem("item_db_etc.yml",    "Etc"));
        if (!todos.isEmpty()) {
            itemRepo.saveAll(todos);
            log.info("Imported {} items total.", todos.size());
        }
    }

    private List<ItemEntity> parseItem(String filename, String label) {
        log.info("  Parsing {} items...", label);
        List<ItemEntity> itens = itemParser.parse(readClasspath("rathena/" + filename));
        log.info("  Parsed {} items from {}.", itens.size(), label);
        return itens;
    }

    private String readClasspath(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read local rAthena file: " + path, e);
        }
    }
}
