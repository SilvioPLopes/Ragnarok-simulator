package [PACOTE_BASE].populator;

import [PACOTE_BASE].populator.dto.ItemClientData;
import [PACOTE_BASE].[REPOSITORY_ITEM];

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Popula os campos de cliente (sprite, nome, descrição) nos itens já existentes no banco.
 * Deve ser chamado APÓS o populador do item_db.yml do rAthena.
 *
 * Configurar no application.properties:
 *   ro.assets.iteminfo-lua-path=C:/ro-assets/iteminfo.lua
 *   ro.assets.icons-bmp-path=C:/ro-assets/texture/item/
 *   ro.assets.icons-output-path=src/main/resources/static/assets/items/
 */
@Component
public class ItemInfoPopulator {

    private final [REPOSITORY_ITEM] itemRepository;
    private final SpriteConverter spriteConverter;
    private final ItemInfoLuaParser parser;

    @Value("${ro.assets.iteminfo-lua-path}")
    private String itemInfoLuaPath;

    @Value("${ro.assets.icons-bmp-path}")
    private String iconsBmpPath;

    @Value("${ro.assets.icons-output-path}")
    private String iconsOutputPath;

    public ItemInfoPopulator([REPOSITORY_ITEM] itemRepository) {
        this.itemRepository = itemRepository;
        this.spriteConverter = new SpriteConverter();
        this.parser = new ItemInfoLuaParser();
    }

    /**
     * Passo 1: converte BMPs para PNGs na pasta de assets estáticos.
     * Rodar uma vez. Pode pular se os PNGs já existirem.
     */
    public void convertSprites() throws IOException {
        System.out.println("Convertendo BMPs para PNG...");
        spriteConverter.convertAll(
                Paths.get(iconsBmpPath),
                Paths.get(iconsOutputPath)
        );
        System.out.println("Conversão concluída.");
    }

    /**
     * Passo 2: lê iteminfo.lua e atualiza os itens no banco.
     */
    public void populateClientData() throws IOException {
        System.out.println("Lendo iteminfo.lua...");
        Map<Integer, ItemClientData> clientData = parser.parse(itemInfoLuaPath);
        System.out.println("Total de itens no iteminfo.lua: " + clientData.size());

        int updated = 0;
        int notFound = 0;

        for (Map.Entry<Integer, ItemClientData> entry : clientData.entrySet()) {
            int itemId = entry.getKey();
            ItemClientData data = entry.getValue();

            // [METODO_FIND_BY_ITEM_ID] → substitua pelo método real do seu repository
            Optional itemOpt = itemRepository.[METODO_FIND_BY_ITEM_ID](itemId);

            if (itemOpt.isPresent()) {
                var item = itemOpt.get();

                // [CAMPO_DISPLAY_NAME], [CAMPO_SPRITE], [CAMPO_DESCRIPTION]
                // → substitua pelos setters reais da sua entity
                item.[CAMPO_DISPLAY_NAME](data.getDisplayName());
                item.[CAMPO_SPRITE](data.getResourceName());
                item.[CAMPO_DESCRIPTION](String.join("\n", data.getDescription()));

                itemRepository.save(item);
                updated++;
            } else {
                notFound++;
            }
        }

        System.out.printf("Concluído: %d atualizados, %d não encontrados no DB%n",
                updated, notFound);
    }
}