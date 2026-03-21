package com.ragnarok.application.service;

import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.runner.RagnarokTerminalRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class PlayerServiceTest {

    @MockBean
    @SuppressWarnings("unused")
    private RagnarokTerminalRunner ragnarokTerminalRunner;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("Deve criar um Espadachim nv 1 e salvar atributos planos no banco")
    void deveCriarSalvarPlayer() {
        // 1. Ação: Criar personagem
        String nomeHeroi = "Lancelot";
        Player playerCriado = playerService.criarNovoPersonagem(nomeHeroi, "Swordsman");

        // 2. Validação do Retorno (Domínio)
        assertNotNull(playerCriado.getId(), "O ID deve ser gerado pelo banco");
        assertEquals(1, playerCriado.getBaseLevel());
        assertEquals("prontera", playerCriado.getLocation().getMapName());
        assertEquals(1, playerCriado.getStats().getStr());

        // 3. Validação do Banco (Infraestrutura - Flattening)
        Optional<PlayerEntity> banco = playerRepository.findById(playerCriado.getId());

        assertTrue(banco.isPresent());
        assertEquals(nomeHeroi, banco.get().getName());

        // Aqui validamos se o Mapper achatou corretamente:
        // O que era player.getStats().getStr() virou entity.getStr()
        assertEquals(1, banco.get().getStr(), "A Força (STR) deve estar na coluna plana da tabela");
        assertEquals("prontera", banco.get().getMapName(), "O Mapa deve estar na coluna plana da tabela");

        System.out.println("SUCESSO! Player criado com ID: " + playerCriado.getId());
    }

    @Test
    @DisplayName("ressuscitarJogador restaura HP máximo do player ID=1")
    @org.springframework.transaction.annotation.Transactional
    void ressuscitarJogador_restauraHpMaximo() {
        var player = playerRepository.findById(1L).orElseThrow();
        int hpMax = player.getHpMax() != null ? player.getHpMax() : 100;
        player.setHpCurrent(0);
        playerRepository.save(player);

        playerService.ressuscitarJogador(1L);

        var depois = playerRepository.findById(1L).orElseThrow();
        assertEquals(hpMax, depois.getHpCurrent(),
                "HP deve ser restaurado para o máximo após ressurreição");
    }

    @Test
    @DisplayName("ressuscitarJogador lança exceção para player inexistente")
    void ressuscitarJogador_playerInexistente_lancaExcecao() {
        assertThrows(IllegalArgumentException.class,
                () -> playerService.ressuscitarJogador(999999L));
    }
}