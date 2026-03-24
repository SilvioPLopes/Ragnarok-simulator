package com.ragnarok.application.service;

import com.ragnarok.domain.model.Monster;
import com.ragnarok.infrastructure.client.RagnapiClient;
import com.ragnarok.infrastructure.client.dto.MonsterDTO;
import com.ragnarok.infrastructure.client.mapper.MonsterMapper;
import com.ragnarok.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonsterCatalogService {

    private static final Logger log = LoggerFactory.getLogger(MonsterCatalogService.class);

    private final RagnapiClient ragnapiClient;
    private final MonsterRepository monsterRepository;
    private final MonsterMapper monsterMapper;
    private final ItemRepository itemRepository;

    public MonsterCatalogService(RagnapiClient ragnapiClient,
                                 MonsterRepository monsterRepository,
                                 MonsterMapper monsterMapper,
                                 ItemRepository itemRepository) {
        this.ragnapiClient = ragnapiClient;
        this.monsterRepository = monsterRepository;
        this.monsterMapper = monsterMapper;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public Monster carregarESalvarMonstro(Long monsterId) {
        // 1. Fetch from API
        MonsterDTO dto = ragnapiClient.getMonsterById(monsterId);

        // 2. Load managed entity if it already exists in the database
        Monster domain = monsterMapper.toDomain(dto);
        MonsterEntity monsterEntity = monsterRepository.findById(monsterId)
                .orElseGet(() -> monsterMapper.toEntity(domain));

        // 3. Drop mining
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

        // 4. Persist (Cascade handles Drops)
        monsterRepository.save(monsterEntity);

        return monsterMapper.toDomain(dto);
    }

    private ItemEntity criarItemPlaceholder(Long id, String nome, String imgUrl) {
        ItemEntity novoItem = new ItemEntity();
        novoItem.setId(id);
        novoItem.setName(nome);
        novoItem.setImgUrl(imgUrl);
        novoItem.setDescription("Item imported via Monster Drop (Placeholder)");
        return itemRepository.save(novoItem);
    }

    private Long extrairIdDoItem(String urlImagem) {
        try {
            String filename = urlImagem.substring(urlImagem.lastIndexOf("/") + 1);
            String idStr = filename.split("\\.")[0];
            return Long.parseLong(idStr);
        } catch (Exception e) {
            log.warn("Could not extract item ID from URL: {}", urlImagem);
            return null;
        }
    }
}
