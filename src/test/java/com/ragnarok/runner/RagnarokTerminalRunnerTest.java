package com.ragnarok.runner;

import com.ragnarok.application.service.BattleService;
import com.ragnarok.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Scanner;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagnarokTerminalRunnerTest {

    @Mock private BattleService battleService;
    @Mock private PlayerRepository playerRepo;
    @Mock private MonsterSpawnRepository spawnRepo;
    @Mock private MonsterRepository monsterRepo;

    @InjectMocks
    private RagnarokTerminalRunner runner;

    private PlayerEntity playerMock;
    private MonsterEntity monsterMock;

    @BeforeEach
    void setup() {
        // Setup de Dados
        playerMock = new PlayerEntity();
        playerMock.setId(1L);
        playerMock.setHpCurrent(0); // Morto
        playerMock.setHpMax(100);

        monsterMock = new MonsterEntity();
        monsterMock.setId(50L);
        monsterMock.setName("Drops");
        monsterMock.setHp(55);

        // Injeção de Estado Interno via Reflection (Bypassa Scanner/Random hardcoded)
        ReflectionTestUtils.setField(runner, "currentPlayer", new com.ragnarok.domain.model.Player(1L, "Hero", null, null, 1, 1, 0L, 0L, 0L, 0, 0, null, null, null, null, null));
        ReflectionTestUtils.setField(runner, "currentMonster", monsterMock);
        ReflectionTestUtils.setField(runner, "inBattle", true);
    }

    @Test
    @DisplayName("Runner: Deve ressuscitar jogador automaticamente ao receber FATAL do serviço")
    void deveRessuscitarJogadorAposMorte() {
        // 1. Simula Input: "1" (Atacar)
        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        Scanner scannerMock = new Scanner(System.in);
        ReflectionTestUtils.setField(runner, "scanner", scannerMock);

        // 2. Mock do Comportamento
        when(battleService.realizarAtaque(anyLong(), anyLong()))
                .thenReturn("FATAL: Você recebeu dano massivo e morreu.");

        when(playerRepo.findById(1L)).thenReturn(Optional.of(playerMock));

        // 3. Execução (Chama apenas o método de menu para isolar o teste)
        ReflectionTestUtils.invokeMethod(runner, "renderBattleMenu");

        // 4. Validação: Verifica se o jogador foi salvo com HP Cheio
        verify(playerRepo).save(argThat(p -> p.getHpCurrent() == 100));

        // Verifica se saiu de batalha
        assert !((boolean) ReflectionTestUtils.getField(runner, "inBattle"));
    }
}