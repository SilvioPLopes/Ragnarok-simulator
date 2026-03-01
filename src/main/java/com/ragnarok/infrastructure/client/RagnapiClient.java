package com.ragnarok.infrastructure.client;

import com.ragnarok.infrastructure.client.dto.ItemDTO;
import com.ragnarok.infrastructure.client.dto.MonsterDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ragnapi", url = "https://ragnapi.com/api/v1/old-times")
public interface RagnapiClient {

    @GetMapping("/monsters/{id}")
    MonsterDTO getMonsterById(@PathVariable("id") Long id);

    @GetMapping("/items/{id}")
    ItemDTO getItemById(@PathVariable("id") Long id);
}