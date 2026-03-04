package com.ragnarok.runner;

import com.ragnarok.application.service.BattleService;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.*;

import java.util.List;
import java.util.Scanner;
import java.util.Random;

@Component
public class RagnarokTerminalRunner implements CommandLineRunner {

    private final BattleService battleService;
    private final PlayerService playerService;
    private final ItemService itemService;
    private final PlayerRepository playerRepo;
    private final PlayerItemRepository playerItemRepo;
    private final MonsterSpawnRepository spawnRepo;
    private final MonsterRepository monsterRepo;
    private final PlayerMapper playerMapper;

    private final Scanner scanner = new Scanner(System.in);
    private final Random rng = new Random();

    private Player currentPlayer;
    private MonsterEntity currentMonster;
    private boolean inBattle = false;

    public RagnarokTerminalRunner(BattleService bs,
                                  PlayerService ps,
                                  ItemService is,
                                  PlayerRepository pr,
                                  PlayerItemRepository pir,
                                  MonsterSpawnRepository msr,
                                  MonsterRepository mr,
                                  PlayerMapper pm) {
        this.battleService = bs;
        this.playerService = ps;
        this.itemService = is;
        this.playerRepo = pr;
        this.playerItemRepo = pir;
        this.spawnRepo = msr;
        this.monsterRepo = mr;
        this.playerMapper = pm;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> INICIANDO RAGNAROK TERMINAL...");
        Long playerId = 1L;

        if (!playerRepo.existsById(playerId)) {
            System.out.println("Player ID 1 não encontrado. Crie um player antes de rodar.");
            return;
        }

        currentPlayer = new Player();
        currentPlayer.setId(playerId);

        gameLoop();
    }

    private void gameLoop() {
        boolean running = true;
        while (running) {
            try {
                if (inBattle) {
                    renderBattleMenu();
                } else {
                    renderExplorationMenu();
                }
            } catch (Exception e) {
                System.out.println("ERRO CRÍTICO: " + e.getMessage());
                e.printStackTrace();
                running = false;
            }
        }
    }

    private void renderExplorationMenu() {
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        System.out.println("\n[MAPA: prt_fild08] (HP: " + p.getHpCurrent() + "/" + p.getHpMax() + ")");

        System.out.println("1. Caminhar");
        System.out.println("2. Sair");
        System.out.println("3. Inventário");
        System.out.println("4. Ver Status"); // Nova Opção
        System.out.print("> ");

        String input = scanner.nextLine();
        if ("1".equals(input)) caminhar();
        else if ("2".equals(input)) System.exit(0);
        else if ("3".equals(input)) renderInventoryMenu();
        else if ("4".equals(input)) renderStatusMenu();
    }

    private void renderStatusMenu() {
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        Player domainPlayer = playerMapper.toDomain(p);

        System.out.println("\n=== STATUS DO PERSONAGEM ===");
        System.out.println("Nome: " + p.getName() + " | Classe: " + p.getJobClass());
        System.out.println("----------------------------");
        // Mostra (Base + Bônus)
        System.out.printf("STR: %d (+%d) -> Total: %d%n", p.getStr(), domainPlayer.getTotalStr() - p.getStr(), domainPlayer.getTotalStr());
        System.out.printf("AGI: %d (+%d) -> Total: %d%n", p.getAgi(), 0, p.getAgi());
        System.out.printf("VIT: %d (+%d) -> Total: %d%n", p.getVit(), domainPlayer.getTotalVit() - p.getVit(), domainPlayer.getTotalVit());
        System.out.printf("INT: %d (+%d) -> Total: %d%n", p.getIntelligence(), domainPlayer.getTotalInt() - p.getIntelligence(), domainPlayer.getTotalInt());
        System.out.printf("DEX: %d%n", p.getDex());
        System.out.printf("LUK: %d%n", p.getLuk());
        System.out.println("----------------------------");
        System.out.println("Pressione ENTER para voltar...");
        scanner.nextLine();
    }

    // --- NOVA LÓGICA DE INVENTÁRIO NUMÉRICO ---
    private void renderInventoryMenu() {
        System.out.println("\n=== INVENTÁRIO ===");

        // 1. Busca itens no banco
        List<PlayerItemEntity> itens = playerItemRepo.findByPlayerId(currentPlayer.getId());

        if (itens.isEmpty()) {
            System.out.println("(Mochila Vazia)");
            System.out.println("Pressione ENTER para voltar...");
            scanner.nextLine();
            return;
        }

        // 2. Lista visualmente (Índice 1..N)
        for (int i = 0; i < itens.size(); i++) {
            PlayerItemEntity item = itens.get(i);
            String equipadoStr = item.getEquipped() ? "[E] " : ""; // Feedback visual

            System.out.printf("%d. %s%s (x%d)%n",
                    (i + 1), // Mostra 1 para o usuário (humano)
                    equipadoStr,
                    item.getItem().getName(),
                    item.getAmount());
        }

        System.out.println("0. Voltar");
        System.out.print("Digite o número do item para USAR ou EQUIPAR > ");

        try {
            String input = scanner.nextLine();
            int escolha = Integer.parseInt(input);

            if (escolha == 0) return; // Voltar

            // Valida se o número existe na lista
            if (escolha > 0 && escolha <= itens.size()) {
                // TRADUÇÃO: Humano (1) -> Array (0) -> Entidade (UUID)
                PlayerItemEntity itemEscolhido = itens.get(escolha - 1);

                // Chama o serviço que implementamos
                String resultado = itemService.usarItem(itemEscolhido.getId());
                System.out.println(">>> " + resultado);

                // Pausa para ler
                System.out.println("(Pressione ENTER para continuar)");
                scanner.nextLine();
            } else {
                System.out.println("Opção inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Por favor, digite apenas números.");
        }
    }

    private void renderBattleMenu() {
        System.out.println("\n[COMBATE] " + currentMonster.getName() + " (HP: " + currentMonster.getHp() + ") está agressivo!");
        System.out.println("1. Atacar");
        System.out.println("2. Fugir");
        System.out.print("> ");

        String input = scanner.nextLine();
        if ("1".equals(input)) {
            String resultado = battleService.realizarAtaque(currentPlayer.getId(), currentMonster.getId());

            System.out.println("------------------------------------------------");
            System.out.println(resultado);
            System.out.println("------------------------------------------------");

            monsterRepo.findById(currentMonster.getId()).ifPresent(m -> currentMonster = m);

            if (resultado.contains("VITÓRIA")) {
                System.out.println(">>> Monstro derrotado!");
                inBattle = false;
                currentMonster = null;
            } else if (resultado.contains("FATAL")) {
                handlePlayerDeath();
            }

        } else if ("2".equals(input)) {
            System.out.println("Você fugiu com o rabo entre as pernas!");
            inBattle = false;
            currentMonster = null;
        }
    }

    private void caminhar() {
        System.out.println("Caminhando pelos campos...");
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        if (rng.nextInt(100) < 50) {
            iniciarEncontroAleatorio();
        } else {
            System.out.println("Nada aconteceu. A brisa sopra suavemente.");
        }
    }

    private void iniciarEncontroAleatorio() {
        List<MonsterSpawnEntity> spawns = spawnRepo.findByMapId("prt_fild08");
        if (spawns.isEmpty()) {
            System.out.println("O mapa parece deserto...");
            return;
        }

        MonsterSpawnEntity spawn = spawns.get(rng.nextInt(spawns.size()));
        Long monsterId = spawn.getMonster().getId();

        currentMonster = monsterRepo.findById(monsterId)
                .orElseThrow(() -> new IllegalStateException("Monstro spawnado não existe no banco ID: " + monsterId));

        if (currentMonster.getHp() == null || currentMonster.getHp() <= 0) {
            System.err.println("AVISO: Monstro " + currentMonster.getName() + " sem HP definido. Usando 100 como fallback.");
            currentMonster.setHp(100);
            monsterRepo.save(currentMonster);
        }

        inBattle = true;
        System.out.println("!!! UM MONSTRO APARECEU: " + currentMonster.getName() + " !!!");
    }

    private void handlePlayerDeath() {
        inBattle = false;
        currentMonster = null;

        System.out.println("\n>>> VOCÊ MORREU! As Valquírias restauram sua vida em Prontera.");
        playerService.ressuscitarJogador(currentPlayer.getId());
        System.out.println(">>> RENASCIMENTO: HP Restaurado. Pronto para a batalha.\n");
    }

    private int getBaseHp(Long mobId) {
        if(mobId == 1002L) return 50;
        if(mobId == 1008L) return 427;
        if(mobId == 1063L) return 60;
        if(mobId == 1113L) return 55;
        return 100;
    }
}