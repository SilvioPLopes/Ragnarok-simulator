package com.ragnarok.runner.populator;

import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import com.ragnarok.runner.populator.dto.ItemClientData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enriquece os itens já importados do rAthena com dados do cliente bRO:
 * nome de exibição, sprite e descrição.
 *
 * Pré-requisito: RathenaImporter já rodou (tabela items populada).
 * Ativado por: ro.assets.run-populator=true em application.properties.
 */
@Component
public class ItemInfoPopulator {

    private static final Logger log = LoggerFactory.getLogger(ItemInfoPopulator.class);

    private final ItemRepository itemRepository;
    private final SpriteConverter spriteConverter = new SpriteConverter();
    private final ItemInfoLuaParser parser = new ItemInfoLuaParser();

    @Value("${ro.assets.iteminfo-lua-path}")
    private String itemInfoLuaPath;

    @Value("${ro.assets.icons-png-path}")
    private String iconsPngPath;

    @Value("${ro.assets.icons-output-path}")
    private String iconsOutputPath;

    public ItemInfoPopulator(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /** Copia e renomeia os PNGs pelo ID do item para static/assets/items/. */
    public void convertSprites(Map<Integer, ItemClientData> clientData) throws IOException {
        Map<Integer, String> idToResource = clientData.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getResourceName()));

        int copied = spriteConverter.copyAndRenameByItemId(
                Paths.get(iconsPngPath),
                Paths.get(iconsOutputPath),
                idToResource
        );
        log.info("Sprites copiados: {}", copied);
    }

    /** Atualiza name, img_url e description nos itens existentes no banco. */
    public void populateClientData(Map<Integer, ItemClientData> clientData) {
        int updated = 0, notFound = 0;

        for (Map.Entry<Integer, ItemClientData> entry : clientData.entrySet()) {
            int itemId = entry.getKey();
            ItemClientData data = entry.getValue();

            Optional<ItemEntity> itemOpt = itemRepository.findById((long) itemId);
            if (itemOpt.isEmpty()) { notFound++; continue; }

            ItemEntity item = itemOpt.get();
            if (data.getDisplayName() != null) item.setName(data.getDisplayName());
            item.setImgUrl("/assets/items/" + itemId + ".png");
            if (!data.getDescription().isEmpty())
                item.setDescription(String.join("\n", data.getDescription()));

            itemRepository.save(item);
            updated++;
        }
        log.info("ItemInfoPopulator: {} atualizados, {} não encontrados no banco.", updated, notFound);
    }

    /** Ponto de entrada: parse lua → sprites → banco. */
    public void run() throws IOException {
        log.info("Lendo iteminfo.lua em {}...", itemInfoLuaPath);
        Map<Integer, ItemClientData> clientData = parser.parse(itemInfoLuaPath);
        log.info("iteminfo.lua: {} itens lidos.", clientData.size());
        convertSprites(clientData);
        populateClientData(clientData);
    }
}
