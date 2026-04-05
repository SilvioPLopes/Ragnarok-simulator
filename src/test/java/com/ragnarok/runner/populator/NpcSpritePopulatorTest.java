package com.ragnarok.runner.populator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NpcSpritePopulatorTest {

    @Test
    void buildsSpriteUrlsForAllKnownSpriteRefs() {
        NpcSpritePopulator populator = new NpcSpritePopulator(mock(com.ragnarok.infrastructure.persistence.NpcRepository.class));

        Map<String, Integer> constants = Map.of(
                "JT_4_F_KAFRA1", 117,
                "JT_WARPNPC", 45,
                "JT_1_F_01", 66,
                "JT_1_F_MERCHANT_01", 73
        );

        Map<String, String> urls = populator.buildSpriteUrlMap(constants);

        assertThat(urls).containsEntry("kafra",        "/ro-assets/output-npcs/117_0_0.png");
        assertThat(urls).containsEntry("warp_portal",  "/ro-assets/output-npcs/45_0_0.png");
        assertThat(urls).containsEntry("npc_generic",  "/ro-assets/output-npcs/66_0_0.png");
        assertThat(urls).containsEntry("shop_generic", "/ro-assets/output-npcs/73_0_0.png");
    }

    @Test
    void ignoresMissingSpriteRefInConstants() {
        NpcSpritePopulator populator = new NpcSpritePopulator(mock(com.ragnarok.infrastructure.persistence.NpcRepository.class));

        // Only kafra present — others must be silently omitted
        Map<String, Integer> constants = Map.of("JT_4_F_KAFRA1", 117);

        Map<String, String> urls = populator.buildSpriteUrlMap(constants);

        assertThat(urls).containsOnlyKeys("kafra");
        assertThat(urls.get("kafra")).isEqualTo("/ro-assets/output-npcs/117_0_0.png");
    }
}
