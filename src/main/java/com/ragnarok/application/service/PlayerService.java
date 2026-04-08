package com.ragnarok.application.service;

import com.ragnarok.domain.model.Player;
import com.ragnarok.domain.model.PlayerLocation;
import com.ragnarok.domain.model.PlayerStats;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemRepository;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.PlayerSkillRepository;
import com.ragnarok.domain.exception.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final PlayerItemRepository playerItemRepository;
    private final PlayerSkillRepository playerSkillRepository;

    public PlayerService(PlayerRepository playerRepository, PlayerMapper playerMapper,
                         PlayerItemRepository playerItemRepository,
                         PlayerSkillRepository playerSkillRepository) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
        this.playerItemRepository = playerItemRepository;
        this.playerSkillRepository = playerSkillRepository;
    }

    public Player criarNovoPersonagem(String nome, String classe, Long accountId) {
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
        entity.setAccountId(accountId);

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

    /**
     * Distribui pontos de status do player.
     * delta: mapa com chaves "str","agi","vit","int","dex","luk" e os pontos a adicionar em cada.
     */
    @Transactional
    public PlayerEntity distribuirStats(Long playerId, Map<String, Integer> delta) {
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        int totalGasto = delta.values().stream().filter(v -> v != null && v > 0).mapToInt(Integer::intValue).sum();
        if (totalGasto <= 0) throw new GameException("Nenhum ponto distribuído.");

        int disponivel = player.getStatPoints() != null ? player.getStatPoints() : 0;
        if (totalGasto > disponivel) {
            throw new GameException("Pontos insuficientes. Disponível: " + disponivel + ", tentou gastar: " + totalGasto + ".");
        }

        apply(delta, "str", player.getStr(), player::setStr);
        apply(delta, "agi", player.getAgi(), player::setAgi);
        apply(delta, "vit", player.getVit(), player::setVit);
        apply(delta, "int", player.getIntelligence(), player::setIntelligence);
        apply(delta, "dex", player.getDex(), player::setDex);
        apply(delta, "luk", player.getLuk(), player::setLuk);

        player.setStatPoints(disponivel - totalGasto);
        log.info("[PlayerService] Player {} distribuiu {} pontos. Restante: {}.", playerId, totalGasto, disponivel - totalGasto);
        return playerRepository.save(player);
    }

    private void apply(Map<String, Integer> delta, String key, Integer current, java.util.function.Consumer<Integer> setter) {
        Integer val = delta.get(key);
        if (val != null && val > 0) {
            setter.accept((current != null ? current : 0) + val);
        }
    }

    public List<PlayerEntity> listarPersonagens(Long accountId) {
        return playerRepository.findByAccountId(accountId);
    }

    public PlayerEntity buscarPersonagem(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + id));
    }

    @Transactional
    public void deletarPersonagem(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }
        playerItemRepository.deleteByPlayerId(playerId);
        playerSkillRepository.deleteByPlayerId(playerId);
        playerRepository.deleteById(playerId);
    }
}