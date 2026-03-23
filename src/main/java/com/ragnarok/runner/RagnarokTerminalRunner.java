package com.ragnarok.runner;

import com.ragnarok.application.service.BattleService;
import com.ragnarok.application.service.ClassChangeService;
import com.ragnarok.application.service.ItemService;
import com.ragnarok.application.service.PlayerService;
import com.ragnarok.application.service.SkillCombatService;
import com.ragnarok.application.service.SkillRowDTO;
import com.ragnarok.application.service.SkillService;
import com.ragnarok.domain.exception.GameException;
import com.ragnarok.domain.model.ItemType;
import com.ragnarok.domain.model.JobClass;
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
    private final SkillService skillService;
    private final SkillCombatService skillCombatService;
    private final ClassChangeService classChangeService;

    private final Scanner scanner = new Scanner(System.in);
    private final Random rng = new Random();

    private Player currentPlayer;
    private MonsterEntity currentMonster;
    private boolean inBattle = false;
    private boolean trocarPersonagem = false;

    public RagnarokTerminalRunner(BattleService bs, PlayerService ps, ItemService is,
                                  PlayerRepository pr, PlayerItemRepository pir,
                                  MapMonsterRepository mmr, MonsterRepository mr,
                                  PlayerMapper pm, MapPortalRepository portalRepo,
                                  SkillService skillService, SkillCombatService skillCombatService,
                                  ClassChangeService classChangeService) {
        this.battleService      = bs;
        this.playerService      = ps;
        this.itemService        = is;
        this.playerRepo         = pr;
        this.playerItemRepo     = pir;
        this.mapMonsterRepo     = mmr;
        this.monsterRepo        = mr;
        this.playerMapper       = pm;
        this.portalRepo         = portalRepo;
        this.skillService       = skillService;
        this.skillCombatService = skillCombatService;
        this.classChangeService = classChangeService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> INICIANDO RAGNAROK TERMINAL...");
        while (true) {
            if (!renderCharacterSelect()) return;
            trocarPersonagem = false;
            gameLoop();
        }
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private boolean renderCharacterSelect() {
        while (true) {
            clearScreen();
            List<PlayerEntity> players = playerRepo.findAll();

            if (players.isEmpty()) {
                System.out.println("Nenhum personagem cadastrado. Crie um personagem para jogar.");
                return false;
            }

            System.out.println("\n=== SELECIONE SEU PERSONAGEM ===");
            for (int i = 0; i < players.size(); i++) {
                PlayerEntity p = players.get(i);
                String classe = p.getJobClass() != null ? p.getJobClass() : "Novice";
                int level    = p.getBaseLevel()  != null ? p.getBaseLevel()  : 1;
                int hp       = p.getHpCurrent()  != null ? p.getHpCurrent()  : 0;
                int hpMax    = p.getHpMax()       != null ? p.getHpMax()      : 0;
                String mapa  = p.getMapName()     != null ? p.getMapName()    : "prontera";
                System.out.printf("%d. %-12s | %-10s | Base %-3d | HP %d/%d | %s%n",
                        (i + 1), p.getName(), classe, level, hp, hpMax, mapa);
            }
            System.out.println("0. Sair");
            System.out.print("> ");

            int escolha;
            try {
                escolha = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Digite apenas numeros.");
                continue;
            }

            if (escolha == 0) return false;
            if (escolha < 1 || escolha > players.size()) {
                System.out.println("Opcao invalida.");
                continue;
            }

            currentPlayer = new Player();
            currentPlayer.setId(players.get(escolha - 1).getId());
            return true;
        }
    }

    private void gameLoop() {
        while (!trocarPersonagem) {
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
        clearScreen();
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        String mapaAtual = p.getMapName() != null ? p.getMapName() : "prontera";

        System.out.println("\n[MAPA: " + mapaAtual + "] (HP: " + p.getHpCurrent() + "/" + p.getHpMax() + ")");
        System.out.println("1. Cacas monstros");
        System.out.println("2. Portais");
        System.out.println("3. Inventario");
        System.out.println("4. Ver Status");
        System.out.println("5. Usar Skill");
        System.out.println("6. Trocar personagem");
        System.out.println("7. Sair");
        System.out.print("> ");

        String input = scanner.nextLine();
        if ("1".equals(input))      caminhar(mapaAtual);
        else if ("2".equals(input)) renderPortaisMenu(mapaAtual);
        else if ("3".equals(input)) renderInventoryMenu();
        else if ("4".equals(input)) renderStatusMenu();
        else if ("5".equals(input)) renderOutOfBattleSkillMenu();
        else if ("6".equals(input)) {
            inBattle = false;
            currentMonster = null;
            trocarPersonagem = true;
        } else if ("7".equals(input)) System.exit(0);
    }

    private void renderPortaisMenu(String mapaAtual) {
        clearScreen();
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
            clearScreen();
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

            System.out.println("S. Skills");
            System.out.println("C. Trocar Classe");
            System.out.println("0. Voltar");
            System.out.print("> ");

            String input = scanner.nextLine().trim();

            if ("0".equals(input) || input.isEmpty()) return;

            if ("S".equalsIgnoreCase(input)) {
                renderSkillsMenu();
                continue;
            }

            if ("C".equalsIgnoreCase(input)) {
                renderClassChangeMenu();
                continue;
            }

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

    private void renderClassChangeMenu() {
        clearScreen();
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
        List<JobClass> disponiveis = classChangeService.listarClassesDisponiveis(currentPlayer.getId());

        System.out.println("\n=== TROCAR CLASSE ===");
        System.out.printf("Classe atual: %s | Job Level: %d%n", p.getJobClass(), p.getJobLevel() != null ? p.getJobLevel() : 0);

        if (disponiveis.isEmpty()) {
            System.out.println("Nenhuma troca de classe disponivel para sua classe atual.");
            System.out.println("(Pressione ENTER para voltar)");
            scanner.nextLine();
            return;
        }

        System.out.println();
        for (int i = 0; i < disponiveis.size(); i++) {
            JobClass jc = disponiveis.get(i);
            System.out.printf("%d. %-20s — %s (Tier %d)%n", (i + 1), jc.name(), jc.descricao, jc.tier);
        }
        System.out.println("0. Voltar");
        System.out.print("> ");

        int escolha;
        try {
            escolha = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Digite apenas numeros.");
            return;
        }

        if (escolha == 0) return;
        if (escolha < 1 || escolha > disponiveis.size()) {
            System.out.println("Opcao invalida.");
            return;
        }

        JobClass novaClasse = disponiveis.get(escolha - 1);
        try {
            classChangeService.trocarClasse(currentPlayer.getId(), novaClasse);
            System.out.println(">>> Voce agora e um(a) " + novaClasse.name() + "! Job Level resetado para 1.");
        } catch (GameException e) {
            System.out.println(">>> " + e.getMessage());
        }
    }

    private void renderInventoryMenu() {
        clearScreen();
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

    private void renderSkillsMenu() {
        while (true) {
            clearScreen();
            PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();
            int skillPts = p.getSkillPoints() != null ? p.getSkillPoints() : 0;

            List<SkillRowDTO> skills = skillService.listarSkillsDoPlayer(currentPlayer.getId());

            System.out.println("\n=== SKILLS (Skill Points: " + skillPts + ") ===");
            System.out.println("Classe: " + (p.getJobClass() != null ? p.getJobClass().toUpperCase() : "?"));
            System.out.println();

            if (skills.isEmpty()) {
                System.out.println("Nenhuma skill disponivel para sua classe.");
                System.out.println("(Pressione ENTER para voltar)");
                scanner.nextLine();
                return;
            }

            for (int i = 0; i < skills.size(); i++) {
                SkillRowDTO sk = skills.get(i);
                String status;
                if (sk.currentLevel() >= sk.maxLevel()) {
                    status = "[MAX]";
                } else if (sk.currentLevel() > 0 && sk.canLearn()) {
                    status = "[APRENDIDA]";
                } else if (sk.currentLevel() > 0) {
                    status = "[APRENDIDA - " + sk.blockedReason() + "]";
                } else if (!sk.canLearn()) {
                    status = "[BLOQUEADA: " + sk.blockedReason() + "]";
                } else {
                    status = "[DISPONIVEL]";
                }
                System.out.printf("%-3d [%-20s] %-30s Lv %d/%-3d %s%n",
                        (i + 1), sk.aegisName(), sk.name(), sk.currentLevel(), sk.maxLevel(), status);
            }

            System.out.println("0. Voltar");
            System.out.print("> ");

            String input = scanner.nextLine().trim();

            if ("0".equals(input) || input.isEmpty()) return;

            int escolha;
            try {
                escolha = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Digite apenas numeros.");
                continue;
            }

            if (escolha < 1 || escolha > skills.size()) {
                System.out.println("Opcao invalida.");
                continue;
            }

            SkillRowDTO selecionada = skills.get(escolha - 1);

            if (!selecionada.canLearn()) {
                System.out.println(">>> " + selecionada.blockedReason());
                continue;
            }

            try {
                String resultado = skillService.aprenderSkill(currentPlayer.getId(), selecionada.aegisName());
                System.out.println(">>> " + resultado);
            } catch (GameException e) {
                System.out.println(">>> " + e.getMessage());
            }
        }
    }

    private void renderOutOfBattleSkillMenu() {
        clearScreen();
        List<SkillRowDTO> skills = skillService.listarSkillsUsaveisForaDeCombate(currentPlayer.getId());

        System.out.println("\n=== USAR SKILL (Fora de Combate) ===");

        if (skills.isEmpty()) {
            System.out.println("Nenhuma skill de buff ou cura aprendida.");
            System.out.println("(Pressione ENTER para voltar)");
            scanner.nextLine();
            return;
        }

        for (int i = 0; i < skills.size(); i++) {
            SkillRowDTO sk = skills.get(i);
            System.out.printf("%d. %s (Lv %d)%n", (i + 1), sk.aegisName(), sk.currentLevel());
        }
        System.out.println("0. Voltar");
        System.out.print("> ");

        int escolha;
        try {
            escolha = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return;
        }

        if (escolha == 0 || escolha < 1 || escolha > skills.size()) return;

        String aegisName = skills.get(escolha - 1).aegisName();
        try {
            String resultado = skillCombatService.usarSkillEmCombate(currentPlayer.getId(), aegisName, null);
            System.out.println(">>> " + resultado);
        } catch (GameException e) {
            System.out.println(">>> " + e.getMessage());
        }
        System.out.println("(Pressione ENTER para continuar)");
        scanner.nextLine();
    }

    private void renderBattleMenu() {
        clearScreen();
        PlayerEntity p = playerRepo.findById(currentPlayer.getId()).orElseThrow();

        System.out.println("\n================================================");
        System.out.printf("  VOCE: HP %d/%d | SP %d/%d%n",
                p.getHpCurrent(), p.getHpMax(), p.getSpCurrent(), p.getSpMax());
        System.out.printf("  %s: HP %d%n", currentMonster.getName(), currentMonster.getHp());
        System.out.println("================================================");
        System.out.println("1. Atacar");
        System.out.println("2. Usar Skill");
        System.out.println("3. Usar Item");
        System.out.println("4. Fugir");
        System.out.print("> ");

        String input = scanner.nextLine();
        if ("1".equals(input)) {
            String resultado = battleService.realizarAtaque(currentPlayer.getId(), currentMonster.getId());
            System.out.println("------------------------------------------------");
            System.out.println(resultado);

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
            renderBattleSkillMenu();
        } else if ("3".equals(input)) {
            renderBattleItemMenu();
        } else if ("4".equals(input)) {
            System.out.println("Voce fugiu!");
            inBattle = false;
            currentMonster = null;
        }
    }

    private void renderBattleSkillMenu() {
        List<SkillRowDTO> skills = skillService.listarSkillsDoPlayer(currentPlayer.getId())
                .stream()
                .filter(sk -> sk.currentLevel() > 0)
                .toList();

        if (skills.isEmpty()) {
            System.out.println("Voce nao tem skills aprendidas.");
            System.out.println("(Pressione ENTER para voltar)");
            scanner.nextLine();
            return;
        }

        System.out.println("\n=== USAR SKILL ===");
        for (int i = 0; i < skills.size(); i++) {
            SkillRowDTO sk = skills.get(i);
            System.out.printf("%d. %s (Lv %d)%n", (i + 1), sk.aegisName(), sk.currentLevel());
        }
        System.out.println("0. Voltar");
        System.out.print("> ");

        int escolha;
        try {
            escolha = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return;
        }

        if (escolha == 0 || escolha < 1 || escolha > skills.size()) return;

        String aegisName = skills.get(escolha - 1).aegisName();
        try {
            String resultado = skillCombatService.usarSkillEmCombate(currentPlayer.getId(), aegisName, currentMonster.getId());
            System.out.println(">>> " + resultado);
        } catch (GameException e) {
            System.out.println(">>> " + e.getMessage());
        }
        System.out.println("(Pressione ENTER para continuar)");
        scanner.nextLine();
    }

    private void renderBattleItemMenu() {
        List<PlayerItemEntity> consumiveis = playerItemRepo.findByPlayerId(currentPlayer.getId())
                .stream()
                .filter(pi -> pi.getItem().getType() == ItemType.CONSUMABLE)
                .toList();

        if (consumiveis.isEmpty()) {
            System.out.println("Voce nao tem itens consumiveis.");
            System.out.println("(Pressione ENTER para voltar)");
            scanner.nextLine();
            return;
        }

        System.out.println("\n=== USAR ITEM ===");
        for (int i = 0; i < consumiveis.size(); i++) {
            PlayerItemEntity pi = consumiveis.get(i);
            System.out.printf("%d. %s (x%d)%n", (i + 1), pi.getItem().getName(), pi.getAmount());
        }
        System.out.println("0. Voltar");
        System.out.print("> ");

        int escolha;
        try {
            escolha = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return;
        }

        if (escolha == 0 || escolha < 1 || escolha > consumiveis.size()) return;

        try {
            String resultado = itemService.usarItem(consumiveis.get(escolha - 1).getId());
            System.out.println(">>> " + resultado);
        } catch (Exception e) {
            System.out.println(">>> " + e.getMessage());
        }
        System.out.println("(Pressione ENTER para continuar)");
        scanner.nextLine();
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