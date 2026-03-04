package com.ragnarok.runner.importer;

import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
@Order(1)
public class RathenaImporter implements CommandLineRunner {

    private static final String MOB_DB_URL =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/pre-re/mob_db.yml";
    private static final String ITEM_DB_URL =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/item_db.yml";

    private final MonsterRepository monsterRepo;
    private final ItemRepository itemRepo;
    private final MobDbParser mobParser;
    private final ItemDbParser itemParser;

    // ADICIONADO: construtor que o Spring usa para injetar as dependências
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
        if (monsterRepo.count() == 0) {
            System.out.println("🔄 Importando monstros do rAthena...");
            String yaml = downloadYaml(MOB_DB_URL);
            List<MonsterEntity> monsters = mobParser.parse(yaml);
            monsterRepo.saveAll(monsters);
            System.out.println("✅ " + monsters.size() + " monstros importados.");
        } else {
            System.out.println("📦 Monstros já existem no banco. Pulando importação.");
        }

        if (itemRepo.count() == 0) {
            System.out.println("🔄 Importando itens do rAthena...");
            String yaml = downloadYaml(ITEM_DB_URL);
            List<ItemEntity> items = itemParser.parse(yaml);
            itemRepo.saveAll(items);
            System.out.println("✅ " + items.size() + " itens importados.");
        } else {
            System.out.println("📦 Itens já existem no banco. Pulando importação.");
        }
    }

    private String downloadYaml(String url) {
        RestTemplate rest = new RestTemplate();
        return rest.getForObject(url, String.class);
    }
}