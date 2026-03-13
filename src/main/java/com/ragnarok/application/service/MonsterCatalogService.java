package com.ragnarok.application.service;

import com.ragnarok.domain.model.Monster;
import com.ragnarok.infrastructure.client.RagnapiClient;
import com.ragnarok.infrastructure.client.dto.MonsterDTO;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MonsterCatalogService {

    private final RagnapiClient ragnapiClient;
    private final MonsterRepository monsterRepository;
    private final MonsterMapper monsterMapper;
    private final ItemRepository itemRepository;
    private final GameMapRepository gameMapRepository;


    public MonsterCatalogService(RagnapiClient ragnapiClient,
                                 MonsterRepository monsterRepository,
                                 MonsterMapper monsterMapper,
                                 ItemRepository itemRepository,
                                 GameMapRepository gameMapRepository) {
        this.ragnapiClient = ragnapiClient;
        this.monsterRepository = monsterRepository;
        this.monsterMapper = monsterMapper;
        this.itemRepository = itemRepository;
        this.gameMapRepository = gameMapRepository;
    }

    @Transactional
    public Monster carregarESalvarMonstro(Long monsterId) {
        // 1. Busca na API
        MonsterDTO dto = ragnapiClient.getMonsterById(monsterId);

        // 2. Conversão Básica — carrega entidade gerenciada se já existir no banco
        Monster domain = monsterMapper.toDomain(dto);
        MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                .orElseGet(() -> monsterMapper.toEntity(domain));

        // 3. MINERAÇÃO DE DROPS (Lógica existente)
        if (dto.drops() != null) {
            monsterEntity.getDrops().clear();
            for (var dropDto : dto.drops()) {
                Long itemId = extrairIdDoItem(dropDto.img());
                if (itemId != null) {
                    ItemEntity item = itemRepository.findById(itemId)
                            .orElseGet(() -> criarItemPlaceholder(itemId, dropDto.name(), dropDto.img()));
                    monsterEntity.addDrop(item, dropDto.rate());
                }
            }
        }

        // 4. MINERAÇÃO DE MAPAS (Nova Lógica)
        if (dto.maps() != null) {
            monsterEntity.getSpawns().clear(); // Limpa spawns antigos

            for (var mapDto : dto.maps()) {
                // Extrai "moc_fild08" da URL
                String mapId = extrairIdDoMapa(mapDto.img());

                if (mapId != null) {
                    // Busca ou Cria o Mapa
                    GameMapEntity mapEntity = gameMapRepository.findById(mapId)
                            .orElseGet(() -> criarMapaPlaceholder(mapId, mapDto.name(), mapDto.type(), mapDto.img()));

                    // Adiciona o Spawn (Monstro + Mapa + Quantidade)
                    // Nota: O JSON traz "frequency" como texto ("instantly"), salvamos assim mesmo.
                    monsterEntity.addSpawn(mapEntity, mapDto.amount(), mapDto.frequency());
                }
            }
        }

        // 5. Salva Tudo (Cascade cuida dos Spawns e Drops)
        monsterRepository.save(monsterEntity);

        return monsterMapper.toDomain(dto);
    }

    // --- Métodos Auxiliares Privados ---

    private ItemEntity criarItemPlaceholder(Long id, String nome, String imgUrl) {
        ItemEntity novoItem = new ItemEntity();
        novoItem.setId(id);
        novoItem.setName(nome); // Usa o nome que veio no drop ("red_blood")
        novoItem.setImgUrl(imgUrl);
        novoItem.setDescription("Item importado via Monster Drop (Placeholder)");

        // Salva imediatamente para poder ser referenciado
        return itemRepository.save(novoItem);
    }

    private GameMapEntity criarMapaPlaceholder(String mapId, String nome, String tipo, String imgUrl) {
        GameMapEntity novoMapa = new GameMapEntity();
        novoMapa.setId(mapId);
        novoMapa.setName(nome);
        novoMapa.setType(tipo);
        novoMapa.setImgUrl(imgUrl);
        return gameMapRepository.save(novoMapa);
    }

    private Long extrairIdDoItem(String urlImagem) {
        try {
            // Ex: "http://db.irowiki.org/image/item/990.png"
            // Pega o que está entre a última barra '/' e o ponto '.png'
            String filename = urlImagem.substring(urlImagem.lastIndexOf("/") + 1);
            String idStr = filename.split("\\.")[0];
            return Long.parseLong(idStr);
        } catch (Exception e) {
            System.err.println("Erro ao extrair ID do item da URL: " + urlImagem);
            return null; // Ignora drops com URL quebrada
        }
    }

    private String extrairIdDoMapa(String url) {
        try {
            // Ex: "http://.../thumb/moc_fild08.png" -> "moc_fild08"
            String filename = url.substring(url.lastIndexOf("/") + 1);
            return filename.split("\\.")[0];
        } catch (Exception e) { return null; }
    }
}