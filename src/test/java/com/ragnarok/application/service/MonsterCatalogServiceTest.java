package com.ragnarok.application.service;

import com.ragnarok.infrastructure.client.RagnapiClient;
import com.ragnarok.infrastructure.client.dto.MonsterDTO;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.runner.RagnarokTerminalRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class MonsterCatalogServiceTest {

    @MockBean
    @SuppressWarnings("unused")
    private RagnarokTerminalRunner ragnarokTerminalRunner;

    @MockBean
    private RagnapiClient ragnapiClient;

    @Autowired
    private MonsterCatalogService monsterCatalogService;

    @Autowired
    private MonsterRepository monsterRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long MONSTER_ID = 1001L;
    private static final Long ITEM_ID = 990L;

    @BeforeEach
    void limparDadosDeTeste() {
        jdbcTemplate.update("DELETE FROM monster_drops WHERE monster_id = ?", MONSTER_ID);
        jdbcTemplate.update("DELETE FROM map_monsters WHERE monster_id = ?", MONSTER_ID);
        jdbcTemplate.update("DELETE FROM monsters WHERE id = ?", MONSTER_ID);
    }

    private MonsterDTO montarDtoEscorpiao() {
        MonsterDTO.MainStatsDTO stats = new MonsterDTO.MainStatsDTO(
                "460",   // hp
                "14",    // level
                "10",    // def
                "5",     // m_def
                "53~65", // attack
                "0",     // magic_attack
                "140",   // aspd
                "150",   // move_speed
                "144",   // base_exp
                "113",   // job_exp
                "60",    // flee
                "73",    // hit
                "0",     // defense_rating
                "0",     // crit_shield
                "0"      // exp_ratio
        );

        MonsterDTO.MainAtbDTO atb = new MonsterDTO.MainAtbDTO(
                5,  // agi
                5,  // int_val
                5,  // luk
                10, // vit
                15  // dex
        );

        MonsterDTO.DropDTO dropRedBlood = new MonsterDTO.DropDTO(
                "red_blood",
                "http://db.irowiki.org/image/item/990.png",
                100.0
        );

        MonsterDTO.MapDTO mapaDeserto = new MonsterDTO.MapDTO(
                "Morroc Field 08",
                1,
                30,
                "instantly",
                "field",
                "http://db.irowiki.org/thumb/moc_fild08.png"
        );

        return new MonsterDTO(
                "mongo-escorpiao-001",
                MONSTER_ID,
                "Scorpion",
                "Small",
                "Insect",
                "Earth",
                2,
                "http://example.com/scorpion.gif",
                atb,
                stats,
                null,   // elementalDamage
                null,   // skills
                List.of(dropRedBlood),
                List.of(mapaDeserto)
        );
    }

    @Test
    @DisplayName("Should process mocked monster, save it, and create drop placeholders")
    void deveCarregarESalvarMonstroComDrops() {
        when(ragnapiClient.getMonsterById(MONSTER_ID)).thenReturn(montarDtoEscorpiao());

        monsterCatalogService.carregarESalvarMonstro(MONSTER_ID);

        Optional<MonsterEntity> monstro = monsterRepository.findById(MONSTER_ID);
        assertTrue(monstro.isPresent(), "Scorpion (ID=1001) should have been saved to the database");
        assertEquals("Scorpion", monstro.get().getName());

        assertTrue(itemRepository.existsById(ITEM_ID),
                "Item Red Blood (ID=990) should exist after processing drops");
    }
}
