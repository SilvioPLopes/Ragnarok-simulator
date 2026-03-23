package com.ragnarok.runner;

import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class PlayerSeedLoader {

    private static final Logger log = LoggerFactory.getLogger(PlayerSeedLoader.class);

    @Bean
    @Order(2)
    public CommandLineRunner initPlayer(PlayerRepository playerRepo) {
        return args -> {
            if (!playerRepo.existsById(1L)) {
                PlayerEntity p = new PlayerEntity();
                p.setName("Hero");
                p.setJobClass("Novice");
                p.setHpCurrent(100);
                p.setHpMax(500);
                p.setStr(10);
                p.setAgi(10);
                p.setVit(10);
                p.setIntelligence(10);
                p.setDex(10);
                p.setLuk(10);
                p.setBaseExp(0L);
                p.setJobExp(0L);
                p.setBaseLevel(1);
                p.setJobLevel(1);
                p.setStatPoints(0);
                p.setSkillPoints(0);
                p.setMapName("prontera");
                playerRepo.save(p);
                log.info("Player inicial 'Hero' criado.");
            } else {
                // Garante campos não-nulos em saves antigos
                PlayerEntity p = playerRepo.findById(1L).orElseThrow(() -> new RuntimeException("Player seed not found: id=1"));
                boolean dirty = false;
                if (p.getBaseExp()    == null) { p.setBaseExp(0L);    dirty = true; }
                if (p.getJobExp()     == null) { p.setJobExp(0L);     dirty = true; }
                if (p.getStatPoints() == null) { p.setStatPoints(0);  dirty = true; }
                if (p.getSkillPoints()== null) { p.setSkillPoints(0); dirty = true; }
                if (dirty) playerRepo.save(p);
            }
        };
    }
}
