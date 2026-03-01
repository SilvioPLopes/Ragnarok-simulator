package com.ragnarok.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.ItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class ItemDataInitializer implements CommandLineRunner {

    private final ItemRepository itemRepository;
    private final ObjectMapper objectMapper;

    public ItemDataInitializer(ItemRepository itemRepository, ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        try {
            if (itemRepository.count() == 0) {
                System.out.println("📦 Banco de Itens vazio. Iniciando carga do JSON...");

                InputStream inputStream = new ClassPathResource("items.json").getInputStream();
                List<ItemEntity> itens = objectMapper.readValue(inputStream, new TypeReference<>() {});

                itemRepository.saveAll(itens);

                System.out.println("✅ Sucesso! " + itens.size() + " itens foram carregados no banco.");
            } else {
                System.out.println("📦 Banco de Itens já populado. Pulando carga.");
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao carregar itens do JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
}