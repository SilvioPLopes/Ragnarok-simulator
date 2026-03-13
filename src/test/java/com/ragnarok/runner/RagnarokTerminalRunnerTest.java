package com.ragnarok.runner;

import com.ragnarok.application.service.*;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
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
    @Mock private PlayerService playerService;
    @Mock private ItemService itemService;
    @Mock private PlayerRepository playerRepo;
    @Mock private PlayerItemRepository playerItemRepo;
    @Mock private MapMonsterRepository mapMonsterRepo;
    @Mock private MonsterRepository monsterRepo;
    @Mock private PlayerMapper playerMapper;
    @Mock private MapPortalRepository portalRepo;
    @Mock private SkillService skillService;
    @Mock private ClassChangeService classChangeService;

    @InjectMocks
    private RagnarokTerminalRunner runner;

    private PlayerEntity playerMock;
    private MonsterEntity monsterMock;

    @BeforeEach
    void setup() {
        playerMock = new PlayerEntity();
        playerMock.setId(1L);
        playerMock.setHpCurrent(0);
        playerMock.setHpMax(100);

        monsterMock = new MonsterEntity();
        monsterMock.setId(50L);
        monsterMock.setName("Drops");
        monsterMock.setHp(55);

        Player currentPlayer = new Player();
        currentPlayer.setId(1L);
        ReflectionTestUtils.setField(runner, "currentPlayer", currentPlayer);
        ReflectionTestUtils.setField(runner, "currentMonster", monsterMock);
        ReflectionTestUtils.setField(runner, "inBattle", true);
    }

    @Test
    @DisplayName("Runner: Deve ressuscitar jogador automaticamente ao receber FATAL do serviço")
    void deveRessuscitarJogadorAposMorte() {
        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        ReflectionTestUtils.setField(runner, "scanner", new Scanner(System.in));

        when(battleService.realizarAtaque(anyLong(), anyLong()))
                .thenReturn("FATAL: Você recebeu dano massivo e morreu.");
        when(playerRepo.findById(1L)).thenReturn(Optional.of(playerMock));

        ReflectionTestUtils.invokeMethod(runner, "renderBattleMenu");

        verify(playerService).ressuscitarJogador(1L);
        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assert !inBattle;
    }
}
