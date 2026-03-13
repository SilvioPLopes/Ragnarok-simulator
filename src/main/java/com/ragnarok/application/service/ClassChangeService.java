package com.ragnarok.application.service;

import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.SkillTreeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassChangeService {

    private final PlayerRepository playerRepository;
    private final SkillTreeRepository skillTreeRepository;

    public ClassChangeService(PlayerRepository playerRepository,
                              SkillTreeRepository skillTreeRepository) {
        this.playerRepository = playerRepository;
        this.skillTreeRepository = skillTreeRepository;
    }

    /**
     * Retorna as classes disponíveis para o player progredir.
     * NÃO verifica job level — isso é responsabilidade de trocarClasse().
     * Retorna lista vazia se a classe do player não tem caminho de progressão.
     */
    public List<JobClass> listarClassesDisponiveis(Long playerId) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Jogador não encontrado."));

        JobClass current = resolveJobClass(player.getJobClass());

        // Guard antes da chamada ao banco: evita query desnecessária para classes sem progressão
        if (current.tier >= 2 || (current.tier == 0 && current != JobClass.NOVICE)) {
            return Collections.emptyList();
        }

        Set<String> dbClasses = buildDbClassSet();

        if (current == JobClass.NOVICE) {
            return Arrays.stream(JobClass.values())
                    .filter(j -> j.tier == 1 && dbClasses.contains(j.name()))
                    .collect(Collectors.toList());
        }

        // tier == 1: retorna nextClasses() filtradas pelo banco
        return Arrays.stream(current.nextClasses())
                .filter(j -> dbClasses.contains(j.name()))
                .collect(Collectors.toList());
    }

    /**
     * Troca a classe do player após validar todas as regras.
     * Lança IllegalStateException com mensagem PT-BR em caso de violação.
     */
    @Transactional
    public void trocarClasse(Long playerId, JobClass novaClasse) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Jogador não encontrado."));

        JobClass current = resolveJobClass(player.getJobClass());

        // Guard: apenas NOVICE e tier-1 podem trocar de classe
        if (current.tier >= 2 || (current.tier == 0 && current != JobClass.NOVICE)) {
            throw new IllegalStateException("Troca de classe não disponível para esta classe.");
        }

        // Valida que a nova classe está disponível no banco
        Set<String> dbClasses = buildDbClassSet();
        List<JobClass> validTargets;

        if (current == JobClass.NOVICE) {
            validTargets = Arrays.stream(JobClass.values())
                    .filter(j -> j.tier == 1 && dbClasses.contains(j.name()))
                    .collect(Collectors.toList());
        } else {
            validTargets = Arrays.stream(current.nextClasses())
                    .filter(j -> dbClasses.contains(j.name()))
                    .collect(Collectors.toList());
        }

        if (!validTargets.contains(novaClasse)) {
            throw new IllegalStateException("Classe inválida para progressão.");
        }

        // Verifica job level
        int jobLevel = player.getJobLevel() != null ? player.getJobLevel() : 0;
        if (current == JobClass.NOVICE) {
            if (jobLevel < 9) {
                throw new IllegalStateException("Job level insuficiente. Necessário: 9");
            }
        } else if (current.tier == 1) {
            if (jobLevel < 40) {
                throw new IllegalStateException("Job level insuficiente. Necessário: 40");
            }
        } else {
            throw new IllegalStateException("Progressão de classe não suportada para este tier.");
        }

        // Aplica transição
        player.setJobClass(novaClasse.name());
        player.setJobLevel(1);
        player.setJobExp(0L);
        // skillPoints são mantidos intencionalmente
        playerRepository.save(player);
    }

    private JobClass resolveJobClass(String jobClassStr) {
        try {
            return JobClass.valueOf(jobClassStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("JobClass inválida ou não definida: " + jobClassStr);
        }
    }

    private Set<String> buildDbClassSet() {
        return new HashSet<>(skillTreeRepository.findDistinctJobClasses());
    }
}
