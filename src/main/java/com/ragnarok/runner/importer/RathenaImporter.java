package com.ragnarok.runner.importer;

import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.infrastructure.persistence.MonsterEntity;
import com.ragnarok.infrastructure.persistence.MonsterRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
public class RathenaImporter implements CommandLineRunner {

    private static final String MOB_DB_URL =
            "https://raw.githubusercontent.com/rathena/rathena/master/db/re/mob_db.yml";

    // O item_db.yml principal não tem itens — ele importa 3 arquivos separados
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

            List<ItemEntity> todos = new ArrayList<>();
            todos.addAll(parsearArquivo("Consumíveis", ITEM_DB_USABLE));
            todos.addAll(parsearArquivo("Equipamentos", ITEM_DB_EQUIP));
            todos.addAll(parsearArquivo("Etc",          ITEM_DB_ETC));

            itemRepo.saveAll(todos);
            System.out.println("✅ Total: " + todos.size() + " itens importados.");
        } else {
            System.out.println("📦 Itens já existem no banco. Pulando importação.");
        }
    }

    private List<ItemEntity> parsearArquivo(String nome, String url) {
        System.out.println("  ↳ Baixando " + nome + "...");
        String yaml = downloadYaml(url);
        List<ItemEntity> itens = itemParser.parse(yaml);
        System.out.println("  ↳ " + itens.size() + " itens de " + nome + " parseados.");
        return itens;
    }

    private String downloadYaml(String url) {
        RestTemplate rest = new RestTemplate();
        return rest.getForObject(url, String.class);
    }
}