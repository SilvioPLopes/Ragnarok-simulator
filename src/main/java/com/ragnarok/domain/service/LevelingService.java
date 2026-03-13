package com.ragnarok.domain.service;

import com.ragnarok.domain.model.JobClass;
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

        // Resolve JobClass para obter os caps de nível
        JobClass jobClass;
        try {
            jobClass = JobClass.valueOf(player.getJobClass());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("JobClass inválida ou não definida: " + player.getJobClass());
        }
        int maxJobLevel = jobClass.maxJobLevel();

        // 1. Base EXP — não adiciona se já no cap 99
        if (player.getBaseLevel() >= 99) {
            log.append(" (base nível máximo atingido)");
        } else {
            player.setBaseExp(player.getBaseExp() + gainedBaseExp);
            log.append(String.format(" (+%d Base XP)", gainedBaseExp));

            while (player.getBaseLevel() < 99
                    && player.getBaseExp() >= calculateRequiredBaseExp(player.getBaseLevel())) {
                long req = calculateRequiredBaseExp(player.getBaseLevel());
                player.setBaseExp(player.getBaseExp() - req);
                player.setBaseLevel(player.getBaseLevel() + 1);

                int currentPoints = player.getStatPoints() != null ? player.getStatPoints() : 0;
                player.setStatPoints(currentPoints + 5);

                log.append("\n🎉 LEVEL UP! Nível Base ").append(player.getBaseLevel()).append(" alcançado!");
                log.append(" (+5 Pontos de Status)");

                player.setHpCurrent(player.getStats().getMaxHp());
                player.setSpCurrent(player.getStats().getMaxSp());
            }
        }

        // 2. Job EXP — não adiciona se já no cap da classe
        if (player.getJobLevel() >= maxJobLevel) {
            log.append(" (job nível máximo atingido)");
        } else {
            player.setJobExp(player.getJobExp() + gainedJobExp);
            log.append(String.format(" (+%d Job XP)", gainedJobExp));

            while (player.getJobLevel() < maxJobLevel
                    && player.getJobExp() >= calculateRequiredJobExp(player.getJobLevel())) {
                long req = calculateRequiredJobExp(player.getJobLevel());
                player.setJobExp(player.getJobExp() - req);
                player.setJobLevel(player.getJobLevel() + 1);

                int currentSkillPoints = player.getSkillPoints() != null ? player.getSkillPoints() : 0;
                player.setSkillPoints(currentSkillPoints + 1);

                log.append("\n🌟 JOB UP! Nível de Classe ").append(player.getJobLevel()).append(" alcançado!");
                log.append(" (+1 Ponto de Habilidade)");
            }
        }

        return log.toString();
    }
}