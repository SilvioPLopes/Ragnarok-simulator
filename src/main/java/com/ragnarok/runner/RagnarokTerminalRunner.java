package com.ragnarok.runner;

import com.ragnarok.application.service.BattleService;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.domain.model.Player;
import com.ragnarok.infrastructure.persistence.*;
import com.ragnarok.infrastructure.persistence.mapper.PlayerMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
    private final MapMonsterRepository mapMonsterRepo;   // NOVO: substitui MonsterSpawnRepository
    private final MonsterRepository monsterRepo;
    private final PlayerMapper playerMapper;
    private final MapPortalRepository portalRepo;

    private final Scanner scanner = new Scanner(System.in);
    private final Random rng = new Random();

    private Player currentPlayer;
    private MonsterEntity currentMonster;
    private boolean inBattle = false;

    public RagnarokTerminalRunner(BattleService bs, PlayerService ps, ItemService is,
                                  PlayerRepository pr, PlayerItemRepository pir,
                                  MapMonsterRepository mmr, MonsterRepository mr,
                                  PlayerMapper pm, MapPortalRepository portalRepo) {
        this.battleService    = bs;
        this.playerService    = ps;
        this.itemService      = is;
        this.playerRepo       = pr;
        this.playerItemRepo   = pir;
        this.mapMonsterRepo   = mmr;
        this.monsterRepo      = mr;
        this.playerMapper     = pm;
        this.portalRepo       = portalRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> INICIANDO RAGNAROK TERMINAL...");
        if (!playerRepo.existsById(1L)) {
            System.out.println("Player ID 1 nao encontrado.");
            return;
        }
        currentPlayer = new Player();
        currentPlayer.setId(1L);
        gameLoop();
    }

    private void gameLoop() {
        while (true) {
            try {
                if (inBattle) renderBattleMenu();
                else renderExplorationMenu();
            } catch (Exception e) {
                System.out.println("ERRO CRITICO: " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
    }

    private void renderExplorationMenu() {
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        String mapaAtual = p.getMapName() != null ? p.getMapName() : "prontera";

        System.out.println("\n[MAPA: " + mapaAtual + "] (HP: " + p.getHpCurrent() + "/" + p.getHpMax() + ")");
        System.out.println("1. Cacas monstros");
        System.out.println("2. Portais");
        System.out.println("3. Inventario");
        System.out.println("4. Ver Status");
        System.out.println("5. Sair");
        System.out.print("> ");

        String input = scanner.nextLine();
        if ("1".equals(input))      caminhar(mapaAtual);
        else if ("2".equals(input)) renderPortaisMenu(mapaAtual);
        else if ("3".equals(input)) renderInventoryMenu();
        else if ("4".equals(input)) renderStatusMenu();
        else if ("5".equals(input)) System.exit(0);
    }

    private void renderPortaisMenu(String mapaAtual) {
        List<String> destinos = portalRepo.findDestinosByMapFrom(mapaAtual)
                .stream().filter(d -> !d.equals(mapaAtual)).toList();

        if (destinos.isEmpty()) {
            System.out.println("Nenhum portal disponivel neste mapa.");
            System.out.println("(Pressione ENTER para voltar)");
            scanner.nextLine();
            return;
        }

        System.out.println("\n=== PORTAIS DISPONIVEIS ===");
        for (int i = 0; i < destinos.size(); i++) {
            System.out.printf("%d. -> %s%n", (i + 1), destinos.get(i));
        }
        System.out.println("0. Voltar");
        System.out.print("> ");

        try {
            int escolha = Integer.parseInt(scanner.nextLine());
            if (escolha == 0) return;
            if (escolha > 0 && escolha <= destinos.size()) {
                moverParaMapa(destinos.get(escolha - 1));
            } else {
                System.out.println("Opcao invalida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Digite apenas numeros.");
        }
    }

    private void moverParaMapa(String destino) {
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        p.setMapName(destino);
        playerRepo.save(p);
        System.out.println(">>> Voce viajou para: " + destino);
    }

    private void renderStatusMenu() {
        while (true) {
            PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
            Player domainPlayer = playerMapper.toDomain(p);
            int pontos = p.getStatPoints() != null ? p.getStatPoints() : 0;

            System.out.println("\n=== STATUS DO PERSONAGEM ===");
            System.out.println("Nome: " + p.getName() + " | Classe: " + p.getJobClass());
            System.out.println("Mapa: " + (p.getMapName() != null ? p.getMapName() : "prontera"));
            System.out.println("----------------------------");
            System.out.printf("1. STR: %d (+%d) = %d%n", p.getStr(), domainPlayer.getTotalStr() - p.getStr(), domainPlayer.getTotalStr());
            System.out.printf("2. AGI: %d (+%d) = %d%n", p.getAgi(), domainPlayer.getTotalAgi() - p.getAgi(), domainPlayer.getTotalAgi());
            System.out.printf("3. VIT: %d (+%d) = %d%n", p.getVit(), domainPlayer.getTotalVit() - p.getVit(), domainPlayer.getTotalVit());
            System.out.printf("4. INT: %d (+%d) = %d%n", p.getIntelligence(), domainPlayer.getTotalInt() - p.getIntelligence(), domainPlayer.getTotalInt());
            System.out.printf("5. DEX: %d (+%d) = %d%n", p.getDex(), domainPlayer.getTotalDex() - p.getDex(), domainPlayer.getTotalDex());
            System.out.printf("6. LUK: %d (+%d) = %d%n", p.getLuk(), domainPlayer.getTotalLuk() - p.getLuk(), domainPlayer.getTotalLuk());
            System.out.println("----------------------------");
            System.out.printf("ATK : %-5d | MATK: %d%n", domainPlayer.getTotalAtk(), domainPlayer.getTotalMAtk());
            System.out.printf("DEF : %-5d | HIT : %d%n", domainPlayer.getTotalDef(), domainPlayer.getTotalHit());
            System.out.printf("FLEE: %-5d |%n", domainPlayer.getTotalFlee());
            System.out.println("----------------------------");
            System.out.printf("Base EXP: %d | Job EXP: %d%n", p.getBaseExp(), p.getJobExp());

            if (pontos > 0) {
                System.out.printf(">>> Pontos disponiveis: %d — Digite 1-6 para distribuir%n", pontos);
            } else {
                System.out.println("Stat Points: 0");
            }

            System.out.println("0. Voltar");
            System.out.print("> ");

            String input = scanner.nextLine().trim();

            if ("0".equals(input) || input.isEmpty()) return;

            if (pontos <= 0) {
                System.out.println("Sem pontos para distribuir.");
                continue;
            }

            int stat;
            try {
                stat = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Digite apenas numeros.");
                continue;
            }

            if (stat < 1 || stat > 6) {
                System.out.println("Opcao invalida.");
                continue;
            }

            // Aplica o ponto no stat escolhido
            switch (stat) {
                case 1 -> p.setStr(p.getStr() + 1);
                case 2 -> p.setAgi(p.getAgi() + 1);
                case 3 -> p.setVit(p.getVit() + 1);
                case 4 -> p.setIntelligence(p.getIntelligence() + 1);
                case 5 -> p.setDex(p.getDex() + 1);
                case 6 -> p.setLuk(p.getLuk() + 1);
            }
            p.setStatPoints(pontos - 1);
            playerRepo.save(p);

            String[] nomes = {"STR", "AGI", "VIT", "INT", "DEX", "LUK"};
            System.out.println(">>> " + nomes[stat - 1] + " aumentou!");
        }
    }

    private void renderInventoryMenu() {
        System.out.println("\n=== INVENTARIO ===");
        List<PlayerItemEntity> itens = playerItemRepo.findByPlayerId(currentPlayer.getId());

        if (itens.isEmpty()) {
            System.out.println("(Mochila Vazia)");
            System.out.println("Pressione ENTER para voltar...");
            scanner.nextLine();
            return;
        }

        for (int i = 0; i < itens.size(); i++) {
            PlayerItemEntity item = itens.get(i);
            String eq = item.getEquipped() ? "[E] " : "";
            System.out.printf("%d. %s%s (x%d)%n", (i + 1), eq, item.getItem().getName(), item.getAmount());
        }

        System.out.println("0. Voltar");
        System.out.print("Digite o numero do item > ");

        try {
            int escolha = Integer.parseInt(scanner.nextLine());
            if (escolha == 0) return;
            if (escolha > 0 && escolha <= itens.size()) {
                String resultado = itemService.usarItem(itens.get(escolha - 1).getId());
                System.out.println(">>> " + resultado);
                System.out.println("(Pressione ENTER para continuar)");
                scanner.nextLine();
            } else {
                System.out.println("Opcao invalida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Digite apenas numeros.");
        }
    }

    private void renderBattleMenu() {
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();

        System.out.println("\n================================================");
        System.out.printf("  VOCE: HP %d/%d%n", p.getHpCurrent(), p.getHpMax());
        System.out.printf("  %s: HP %d%n", currentMonster.getName(), currentMonster.getHp());
        System.out.println("================================================");
        System.out.println("1. Atacar");
        System.out.println("2. Fugir");
        System.out.print("> ");

        String input = scanner.nextLine();
        if ("1".equals(input)) {
            String resultado = battleService.realizarAtaque(currentPlayer.getId(), currentMonster.getId());
            System.out.println("------------------------------------------------");
            System.out.println(resultado);

            // Mostra HP atualizado do jogador após contra-ataque
            PlayerEntity pAtualizado = playerRepo.findById(currentPlayer.getId()).orElseThrow();
            System.out.printf("  >> Seu HP atual: %d/%d%n", pAtualizado.getHpCurrent(), pAtualizado.getHpMax());
            System.out.println("------------------------------------------------");

            monsterRepo.findById(currentMonster.getId()).ifPresent(m -> currentMonster = m);

            if (resultado.contains("VITORIA") || resultado.contains("VITÓRIA")) {
                inBattle = false;
                currentMonster = null;
            } else if (resultado.contains("FATAL")) {
                handlePlayerDeath();
            }
        } else if ("2".equals(input)) {
            System.out.println("Voce fugiu!");
            inBattle = false;
            currentMonster = null;
        }
    }

    private void caminhar(String mapaAtual) {
        System.out.println("Cacando em " + mapaAtual + "...");
        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (rng.nextInt(100) < 70) {
            iniciarEncontroAleatorio(mapaAtual);
        } else {
            System.out.println("Nenhum monstro por aqui.");
        }
    }

    private void iniciarEncontroAleatorio(String mapaAtual) {
        List<MapMonsterEntity> entradas = mapMonsterRepo.findByMapId(mapaAtual);

        if (entradas.isEmpty()) {
            System.out.println("Nenhum monstro registrado neste mapa ainda.");
            return;
        }

        // Sorteio ponderado pelo amount
        // Ex: Poring x87, Lunatic x67 → Poring tem mais chance proporcional
        int totalPeso = entradas.stream().mapToInt(e -> e.getAmount() != null ? e.getAmount() : 1).sum();
        int sorteio = rng.nextInt(totalPeso);

        MapMonsterEntity escolhida = null;
        int acumulado = 0;
        for (MapMonsterEntity entrada : entradas) {
            acumulado += entrada.getAmount() != null ? entrada.getAmount() : 1;
            if (sorteio < acumulado) {
                escolhida = entrada;
                break;
            }
        }

        if (escolhida == null) escolhida = entradas.get(0);

        currentMonster = escolhida.getMonster();

        if (currentMonster.getHp() == null || currentMonster.getHp() <= 0) {
            currentMonster.setHp(100);
            monsterRepo.save(currentMonster);
        }

        inBattle = true;
        System.out.println("!!! " + currentMonster.getName().toUpperCase() + " APARECEU !!!");
    }

    private void handlePlayerDeath() {
        inBattle = false;
        currentMonster = null;
        System.out.println("\n>>> VOCE MORREU! Ressuscitando em Prontera...");
        playerService.ressuscitarJogador(currentPlayer.getId());
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        p.setMapName("prontera");
        playerRepo.save(p);
        System.out.println(">>> HP restaurado. Voce esta em Prontera.\n");
    }
}