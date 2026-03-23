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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
@Profile("!test")
public class RathenaImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RathenaImporter.class);

    private static final String MOB_DB_URL =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/mob_db.yml";
    private static final String ITEM_DB_USABLE =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/item_db_usable.yml";
    private static final String ITEM_DB_EQUIP =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/item_db_equip.yml";
    private static final String ITEM_DB_ETC =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/item_db_etc.yml";

    private final MonsterRepository monsterRepo;
    private final ItemRepository itemRepo;
    private final MobDbParser mobParser;
    private final ItemDbParser itemParser;
    private final RathenaDownloadService downloadService;

    public RathenaImporter(MonsterRepository monsterRepo,
                           ItemRepository itemRepo,
                           MobDbParser mobParser,
                           ItemDbParser itemParser,
                           RathenaDownloadService downloadService) {
        this.monsterRepo = monsterRepo;
        this.itemRepo    = itemRepo;
        this.mobParser   = mobParser;
        this.itemParser  = itemParser;
        this.downloadService = downloadService;
    }

    @Override
    public void run(String... args) {
        if (monsterRepo.count() == 0) {
            log.info("Importing monsters from rAthena...");
            String yaml = downloadService.download(MOB_DB_URL);
            if (yaml != null) {
                List<MonsterEntity> monsters = mobParser.parse(yaml);
                monsterRepo.saveAll(monsters);
                log.info("Imported {} monsters.", monsters.size());
            } else {
                log.warn("Monster import skipped — download returned null.");
            }
        } else {
            log.info("Monsters already exist in the database. Skipping import.");
        }

        if (itemRepo.count() == 0) {
            log.info("Importing items from rAthena...");
            List<ItemEntity> todos = new ArrayList<>();
            todos.addAll(parsearArquivo("Usable", ITEM_DB_USABLE));
            todos.addAll(parsearArquivo("Equip",  ITEM_DB_EQUIP));
            todos.addAll(parsearArquivo("Etc",    ITEM_DB_ETC));
            if (!todos.isEmpty()) {
                itemRepo.saveAll(todos);
                log.info("Imported {} items total.", todos.size());
            }
        } else {
            log.info("Items already exist in the database. Skipping import.");
        }
    }

    private List<ItemEntity> parsearArquivo(String nome, String url) {
        log.info("  Downloading {} items...", nome);
        String yaml = downloadService.download(url);
        if (yaml == null) {
            log.warn("  {} item download skipped — download returned null.", nome);
            return List.of();
        }
        List<ItemEntity> itens = itemParser.parse(yaml);
        log.info("  Parsed {} items from {}.", itens.size(), nome);
        return itens;
    }
}
