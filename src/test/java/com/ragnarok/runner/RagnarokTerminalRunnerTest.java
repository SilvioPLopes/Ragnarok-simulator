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
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    @Mock private SkillCombatService skillCombatService;
    @Mock private ClassChangeService classChangeService;

    @InjectMocks
    private RagnarokTerminalRunner runner;

    private PlayerEntity playerMock;
    private MonsterEntity monsterMock;

    @BeforeEach
    void setup() {
        playerMock = new PlayerEntity();
        playerMock.setId(1L);
        playerMock.setHpCurrent(100);
        playerMock.setHpMax(100);
        playerMock.setSpCurrent(40);
        playerMock.setSpMax(40);

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

    // ── Teste original: Ressuscitar após FATAL ────────────────────────────────

    @Test
    @DisplayName("Runner: Deve resetar estado de batalha ao receber FATAL do serviço")
    void deveRessuscitarJogadorAposMorte() {
        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        ReflectionTestUtils.setField(runner, "scanner", new Scanner(System.in));

        when(battleService.realizarAtaque(anyLong(), anyLong()))
                .thenReturn("FATAL: Você recebeu dano massivo e morreu.");
        when(playerRepo.findById(1L)).thenReturn(Optional.of(playerMock));

        ReflectionTestUtils.invokeMethod(runner, "renderBattleMenu");

        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assertFalse(inBattle);
        assertNull(ReflectionTestUtils.getField(runner, "currentMonster"),
                "currentMonster deve ser null após morte");
    }

    // ── moverParaMapa: troca mapName e salva player ───────────────────────────

    @Test
    @DisplayName("moverParaMapa: atualiza mapName do player e persiste no repositório")
    void moverParaMapa_atualizaMapaEPersiste() {
        when(playerRepo.findById(1L)).thenReturn(Optional.of(playerMock));

        ReflectionTestUtils.invokeMethod(runner, "moverParaMapa", "prt_fild08");

        assertEquals("prt_fild08", playerMock.getMapName(),
                "mapName deve ser atualizado para o destino");
        verify(playerRepo).save(playerMock);
    }

    // ── handlePlayerDeath: inBattle=false, currentMonster=null ───────────────

    @Test
    @DisplayName("handlePlayerDeath: reseta inBattle e currentMonster (ressurreição delegada ao BattleEventHandler)")
    void handlePlayerDeath_resetaBattleEMoveParaProntera() {
        ReflectionTestUtils.invokeMethod(runner, "handlePlayerDeath");

        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assertFalse(inBattle, "inBattle deve ser false após morte");

        assertNull(ReflectionTestUtils.getField(runner, "currentMonster"),
                "currentMonster deve ser null após morte");
    }

    // ── caminhar: branch 70% — encontro com monstro ──────────────────────────

    @Test
    @DisplayName("caminhar: 70% branch — inicia encontro aleatório quando rng < 70")
    void caminhar_encontro_iniciaBatalha() {
        Random mockRng = mock(Random.class);
        // nextInt(87) → weighted selection returns 0 (first monster)
        // nextInt(100) → encounter roll returns 50 (< 70 triggers encounter)
        // Order matters: more specific stub must be registered last so it takes precedence
        lenient().when(mockRng.nextInt(anyInt())).thenReturn(0);
        when(mockRng.nextInt(100)).thenReturn(50);
        ReflectionTestUtils.setField(runner, "rng", mockRng);
        ReflectionTestUtils.setField(runner, "inBattle", false);

        // Setup mapa com 1 monstro
        MapMonsterEntity mapMonster = new MapMonsterEntity();
        MonsterEntity poring = new MonsterEntity();
        poring.setId(1L);
        poring.setName("Poring");
        poring.setHp(100);
        mapMonster.setMonster(poring);
        mapMonster.setAmount(87);

        when(mapMonsterRepo.findByMapId("prontera")).thenReturn(List.of(mapMonster));

        ReflectionTestUtils.invokeMethod(runner, "caminhar", "prontera");

        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assertTrue(inBattle, "inBattle deve ser true após encontro");

        MonsterEntity current = (MonsterEntity) ReflectionTestUtils.getField(runner, "currentMonster");
        assertNotNull(current);
        assertEquals("Poring", current.getName());
    }

    // ── caminhar: branch 30% — nenhum monstro encontrado ────────────────────

    @Test
    @DisplayName("caminhar: 30% branch — sem encontro quando rng >= 70")
    void caminhar_semEncontro_naoInterageComRepositorio() {
        Random mockRng = mock(Random.class);
        when(mockRng.nextInt(100)).thenReturn(75); // >= 70 → sem encontro
        ReflectionTestUtils.setField(runner, "rng", mockRng);

        ReflectionTestUtils.invokeMethod(runner, "caminhar", "prontera");

        verifyNoInteractions(mapMonsterRepo);
    }

    // ── iniciarEncontroAleatorio: mapa vazio ─────────────────────────────────

    @Test
    @DisplayName("iniciarEncontroAleatorio: mapa sem monstros não inicia batalha")
    void iniciarEncontroAleatorio_mapaVazio_naoPodeIniciarBatalha() {
        ReflectionTestUtils.setField(runner, "inBattle", false);
        when(mapMonsterRepo.findByMapId("aldebaran")).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(runner, "iniciarEncontroAleatorio", "aldebaran");

        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assertFalse(inBattle, "inBattle deve permanecer false se mapa não tem monstros");
    }

    // ── renderCharacterSelect ─────────────────────────────────────────────────

    @Test
    @DisplayName("renderCharacterSelect: sem players no banco retorna false sem entrar no jogo")
    void renderCharacterSelect_semPlayers_retornaFalse() {
        when(playerRepo.findAll()).thenReturn(List.of());

        boolean resultado = (boolean) ReflectionTestUtils.invokeMethod(runner, "renderCharacterSelect");

        assertFalse(resultado, "deve retornar false quando não há personagens cadastrados");
    }

    @Test
    @DisplayName("renderCharacterSelect: opção 0 (Sair) retorna false")
    void renderCharacterSelect_opcaoZero_retornaFalse() {
        PlayerEntity p = buildPlayer(1L, "Hero", "Novice", 10, 129, 500, "prontera");
        when(playerRepo.findAll()).thenReturn(List.of(p));

        System.setIn(new ByteArrayInputStream("0\n".getBytes()));
        ReflectionTestUtils.setField(runner, "scanner", new Scanner(System.in));

        boolean resultado = (boolean) ReflectionTestUtils.invokeMethod(runner, "renderCharacterSelect");

        assertFalse(resultado, "opção 0 deve encerrar sem selecionar personagem");
    }

    @Test
    @DisplayName("renderCharacterSelect: selecionar player válido seta currentPlayer e retorna true")
    void renderCharacterSelect_playerValido_setaCurrentPlayerERetornaTrue() {
        PlayerEntity p = buildPlayer(7L, "Mirela", "Mage", 25, 180, 180, "geffen");
        when(playerRepo.findAll()).thenReturn(List.of(p));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        ReflectionTestUtils.setField(runner, "scanner", new Scanner(System.in));

        boolean resultado = (boolean) ReflectionTestUtils.invokeMethod(runner, "renderCharacterSelect");

        assertTrue(resultado, "deve retornar true ao selecionar um personagem válido");
        Player current = (Player) ReflectionTestUtils.getField(runner, "currentPlayer");
        assertNotNull(current, "currentPlayer não pode ser null após seleção");
        assertEquals(7L, current.getId(), "currentPlayer deve ter o ID do personagem escolhido");
    }

    @Test
    @DisplayName("renderCharacterSelect: selecionar entre múltiplos players seta o ID correto")
    void renderCharacterSelect_multiplosPlayers_setaIdCorreto() {
        PlayerEntity p1 = buildPlayer(1L, "Hero",   "Novice",    10, 129, 500, "prontera");
        PlayerEntity p2 = buildPlayer(2L, "Aldric", "Swordsman", 15, 250, 250, "prontera");
        PlayerEntity p3 = buildPlayer(3L, "Mirela", "Mage",      25, 180, 180, "geffen");
        when(playerRepo.findAll()).thenReturn(List.of(p1, p2, p3));

        System.setIn(new ByteArrayInputStream("2\n".getBytes()));
        ReflectionTestUtils.setField(runner, "scanner", new Scanner(System.in));

        ReflectionTestUtils.invokeMethod(runner, "renderCharacterSelect");

        Player current = (Player) ReflectionTestUtils.getField(runner, "currentPlayer");
        assertEquals(2L, current.getId(), "deve selecionar o segundo personagem da lista");
    }

    // ── renderExplorationMenu — opção 6 (Trocar personagem) ──────────────────

    @Test
    @DisplayName("renderExplorationMenu: opção 6 reseta estado de batalha e sinaliza troca de personagem")
    void renderExplorationMenu_opcao6_resetaBatalhaESetaTrocarPersonagem() {
        PlayerEntity p = buildPlayer(1L, "Hero", "Novice", 10, 129, 500, "prontera");
        when(playerRepo.findById(1L)).thenReturn(Optional.of(p));

        ReflectionTestUtils.setField(runner, "inBattle", true);
        ReflectionTestUtils.setField(runner, "currentMonster", monsterMock);
        ReflectionTestUtils.setField(runner, "trocarPersonagem", false);

        System.setIn(new ByteArrayInputStream("6\n".getBytes()));
        ReflectionTestUtils.setField(runner, "scanner", new Scanner(System.in));

        ReflectionTestUtils.invokeMethod(runner, "renderExplorationMenu");

        assertTrue((boolean) ReflectionTestUtils.getField(runner, "trocarPersonagem"),
                "trocarPersonagem deve ser true para sinalizar saída do gameLoop");
        assertFalse((boolean) ReflectionTestUtils.getField(runner, "inBattle"),
                "inBattle deve ser resetado para false ao trocar personagem");
        assertNull(ReflectionTestUtils.getField(runner, "currentMonster"),
                "currentMonster deve ser null ao trocar personagem");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlayerEntity buildPlayer(Long id, String name, String jobClass,
                                     int baseLevel, int hp, int hpMax, String mapa) {
        PlayerEntity p = new PlayerEntity();
        p.setId(id);
        p.setName(name);
        p.setJobClass(jobClass);
        p.setBaseLevel(baseLevel);
        p.setHpCurrent(hp);
        p.setHpMax(hpMax);
        p.setMapName(mapa);
        return p;
    }
}
