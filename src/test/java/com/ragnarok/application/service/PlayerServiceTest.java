package com.ragnarok.application.service;

import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
        "DB_USER=postgres",
        "DB_PASSWORD=postgre"
})
class PlayerServiceTest {

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
}