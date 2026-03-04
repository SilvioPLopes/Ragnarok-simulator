package com.ragnarok.domain.service;

import com.ragnarok.domain.model.Player;
import org.springframework.stereotype.Service;

@Service
public class LevelingService {


    public long calculateRequiredBaseExp(int currentLevel) {
        return (long) currentLevel * 100;
    }

    public long calculateRequiredJobExp(int currentLevel) {
        return (long) currentLevel * 100;
    }

    public String processarExperiencia(Player player, long gainedBaseExp, long gainedJobExp) {
        StringBuilder log = new StringBuilder();

        // 1. Adiciona EXP Base
        player.setBaseExp(player.getBaseExp() + gainedBaseExp);
        log.append(String.format(" (+%d Base XP)", gainedBaseExp));

        // 2. Verifica Level Up Base (While para caso ganhe muita XP e upe vários níveis)
        while (player.getBaseExp() >= calculateRequiredBaseExp(player.getBaseLevel())) {
            long req = calculateRequiredBaseExp(player.getBaseLevel());
            player.setBaseExp(player.getBaseExp() - req);
            player.setBaseLevel(player.getBaseLevel() + 1);

            // Recompensa: +5 Pontos de Atributo por nível (Padrão Ragnarok)
            int currentPoints = player.getStatPoints() != null ? player.getStatPoints() : 0;
            player.setStatPoints(currentPoints + 5);

            log.append("\n🎉 LEVEL UP! Nível Base ").append(player.getBaseLevel()).append(" alcançado!");
            log.append(" (+5 Pontos de Status)");

            player.setHpCurrent(player.getStats().getMaxHp());
            player.setSpCurrent(player.getStats().getMaxSp());
        }

        // 3. Adiciona EXP Job
        player.setJobExp(player.getJobExp() + gainedJobExp);
        log.append(String.format(" (+%d Job XP)", gainedJobExp));

        // 4. Verifica Level Up Job
        while (player.getJobExp() >= calculateRequiredJobExp(player.getJobLevel())) {
            long req = calculateRequiredJobExp(player.getJobLevel());
            player.setJobExp(player.getJobExp() - req);
            player.setJobLevel(player.getJobLevel() + 1);

            // Recompensa: +1 Ponto de Skill
            int currentSkillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
            player.setSkillPoints(currentSkillPoints + 1);

            log.append("\n🌟 JOB UP! Nível de Classe ").append(player.getJobLevel()).append(" alcançado!");
            log.append(" (+1 Ponto de Habilidade)");
        }

        return log.toString();
    }
}