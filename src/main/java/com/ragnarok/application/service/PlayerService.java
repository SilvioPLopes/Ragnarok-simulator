package com.ragnarok.application.service;

import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.model.PlayerLocation;
import com.ragnarok.domain.model.PlayerStats;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;

    public PlayerService(PlayerRepository playerRepository, PlayerMapper playerMapper) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
    }

    public Player criarNovoPersonagem(String nome, String classe) {
        if (playerRepository.existsByName(nome)) {
            log.warn("[PlayerService] Tentativa de criar personagem duplicado: nome='{}' já existe no banco.", nome);
            throw new IllegalStateException("Já existe um personagem com o nome: " + nome);
        }
        // 1. Cria o Domínio Puro
        Player novoPlayer = new Player();
        novoPlayer.setName(nome);
        novoPlayer.setJobClass(classe);
        novoPlayer.setBaseLevel(1);
        novoPlayer.setJobLevel(1);
        novoPlayer.setHpCurrent(100);
        novoPlayer.setSpCurrent(40);
        novoPlayer.setBaseExp(0L); // Inicializar para não dar NullPointer
        novoPlayer.setJobExp(0L);
        novoPlayer.setZenny(0L);

        // Stats Iniciais
        PlayerStats stats = new PlayerStats(1, 1, 1, 1, 1, 1, 100, 40);
        novoPlayer.setStats(stats);

        // Localização Inicial
        PlayerLocation loc = new PlayerLocation("prontera", 150.0, 150.0, "prontera");
        novoPlayer.setLocation(loc);

        // 2. Converte para Entity (Infraestrutura)
        PlayerEntity entity = playerMapper.toEntity(novoPlayer);

        // 3. Salva no Banco
        PlayerEntity salvo = playerRepository.save(entity);

        // 4. Retorna o Domínio atualizado
        return playerMapper.toDomain(salvo);
    }

    @Transactional
    public void ressuscitarJogador(Long playerId) {
        PlayerEntity entity = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found para ressurreição"));

        // Recupera HP Maximo (com fallback seguro)
        int maxHp = entity.getHpMax() != null ? entity.getHpMax() : 100;

        entity.setHpCurrent(maxHp);
        // O Hibernate fará o update automático ao fechar a transação, mas o save reforça.
        playerRepository.save(entity);
    }

    public List<PlayerEntity> listarPersonagens() {
        return playerRepository.findAll();
    }

    public PlayerEntity buscarPersonagem(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + id));
    }
}