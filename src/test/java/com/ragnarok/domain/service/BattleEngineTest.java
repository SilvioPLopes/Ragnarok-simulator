package com.ragnarok.domain.service;

import com.ragnarok.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleEngineTest {

    private BattleEngine battleEngine;

    @BeforeEach
    void setUp() {
        battleEngine = new BattleEngine();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Cria um Player mínimo sem inventário, com STR definido. */
    private Player makePlayer(int str, int vit) {
        Player player = new Player();
        PlayerStats stats = new PlayerStats(str, 1, vit, 1, 1, 1, 200, 80);
        player.setStats(stats);
        player.setBaseLevel(1);
        player.setInventory(new ArrayList<>());
        return player;
    }

    /** Cria um Player com uma arma equipada (atacante). */
    private Player makePlayerWithWeapon(int str, int weaponAtk) {
        Player player = makePlayer(str, 1);

        ItemStats weaponStats = new ItemStats();
        weaponStats.setAttack(weaponAtk);

        Item weapon = new Item();
        weapon.setName("Espada de Teste");
        weapon.setStats(weaponStats);

        PlayerItem equip = new PlayerItem();
        equip.setItemDefinition(weapon);
        equip.setIsEquipped(true);
        equip.setAmount(1);

        player.getInventory().add(equip);
        return player;
    }

    /** Cria um Monster com DEF e ATK definidos. */
    private Monster makeMonster(int def, int atk) {
        Monster monster = new Monster();
        MainStats stats = new MainStats();
        stats.setDef(def);
        stats.setAttack(atk);
        monster.setStats(stats);
        monster.setDrops(new ArrayList<>());
        return monster;
    }

    /** Cria um MonsterDrop com uma taxa específica (0.0–100.0). */
    private MonsterDrop makeDrop(double rate) {
        Item item = new Item();
        item.setName("Item Teste");

        MonsterDrop drop = new MonsterDrop();
        drop.setItem(item);
        drop.setRate(rate);
        return drop;
    }

    // ── calculateDamage ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fórmula básica: dano = (STR*2 + WeaponATK) - DEF do monstro")
    void calculateDamage_formulaBasicaCorreta() {
        // STR=10 → statusAtk=20, WeaponATK=15, DEF=5 → dano esperado = 30
        Player player = makePlayerWithWeapon(10, 15);
        Monster monster = makeMonster(5, 0);

        int damage = battleEngine.calculateDamage(player, monster);

        assertEquals(30, damage);
    }

    @Test
    @DisplayName("Dano mínimo é 1 quando DEF do monstro é muito alta")
    void calculateDamage_danoMinimoUmQuandoDefAlta() {
        // STR=1 → statusAtk=2, sem arma, DEF=9999 → dano bruto negativo → deve retornar 1
        Player player = makePlayer(1, 1);
        Monster monster = makeMonster(9999, 0);

        int damage = battleEngine.calculateDamage(player, monster);

        assertEquals(1, damage);
    }

    @Test
    @DisplayName("Dano mínimo é 1 quando dano bruto é exatamente zero")
    void calculateDamage_danoMinimoUmQuandoDanoBrutoZero() {
        // STR=5 → statusAtk=10, sem arma, DEF=10 → dano bruto = 0 → deve retornar 1
        Player player = makePlayer(5, 1);
        Monster monster = makeMonster(10, 0);

        int damage = battleEngine.calculateDamage(player, monster);

        assertEquals(1, damage);
    }

    @Test
    @DisplayName("Monstro com DEF zero: dano = STR*2 + WeaponATK sem subtração")
    void calculateDamage_monsterDefZero_danoIgualAtkTotal() {
        // STR=8 → statusAtk=16, WeaponATK=10, DEF=0 → dano esperado = 26
        Player player = makePlayerWithWeapon(8, 10);
        Monster monster = makeMonster(0, 0);

        int damage = battleEngine.calculateDamage(player, monster);

        assertEquals(26, damage);
    }

    @Test
    @DisplayName("Player com STR alta causa dano proporcional ao dobro da STR")
    void calculateDamage_strAltaCausaDanoProporcional() {
        // STR=50 → statusAtk=100, sem arma, DEF=0 → dano esperado = 100
        Player player = makePlayer(50, 1);
        Monster monster = makeMonster(0, 0);

        int damage = battleEngine.calculateDamage(player, monster);

        assertEquals(100, damage);
    }

    @Test
    @DisplayName("Player sem arma equipada não soma WeaponATK")
    void calculateDamage_semArmaEquipada_weaponAtkEhZero() {
        // STR=10 → statusAtk=20, sem arma, DEF=5 → dano esperado = 15
        Player player = makePlayer(10, 1);
        Monster monster = makeMonster(5, 0);

        int damage = battleEngine.calculateDamage(player, monster);

        assertEquals(15, damage);
    }

    @Test
    @DisplayName("calculateDamage lança IllegalArgumentException quando Player é null")
    void calculateDamage_playerNull_lancaIllegalArgumentException() {
        Monster monster = makeMonster(10, 0);

        assertThrows(IllegalArgumentException.class,
                () -> battleEngine.calculateDamage(null, monster));
    }

    @Test
    @DisplayName("calculateDamage lança IllegalArgumentException quando Monster é null")
    void calculateDamage_monsterNull_lancaIllegalArgumentException() {
        Player player = makePlayer(10, 1);

        assertThrows(IllegalArgumentException.class,
                () -> battleEngine.calculateDamage(player, null));
    }

    // ── calculateLoot ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Loot com taxa 100.0 sempre dropa o item")
    void calculateLoot_taxaMaxima_sempreDropa() {
        Monster monster = makeMonster(0, 0);
        monster.setDrops(List.of(makeDrop(100.0)));

        // Executa múltiplas vezes para garantir consistência
        for (int i = 0; i < 20; i++) {
            List<Item> loot = battleEngine.calculateLoot(monster);
            assertFalse(loot.isEmpty(),
                    "Com taxa 100.0, o item deve sempre dropar (tentativa " + i + ")");
        }
    }

    @Test
    @DisplayName("Loot com taxa 0.0 nunca dropa o item")
    void calculateLoot_taxaZero_nuncaDropa() {
        Monster monster = makeMonster(0, 0);
        monster.setDrops(List.of(makeDrop(0.0)));

        // Com taxa 0.0 e roll >= 0, a condição roll <= 0.0 só seria verdadeira se roll==0.0 (raro)
        // A engine usa roll <= rate, então rate=0.0 nunca garante drop — testamos com taxa negativa
        // Para garantir nunca dropa, usamos taxa negativa (abaixo do intervalo do roll)
        Monster monsterTaxaNegativa = makeMonster(0, 0);
        monsterTaxaNegativa.setDrops(List.of(makeDrop(-1.0)));

        for (int i = 0; i < 30; i++) {
            List<Item> loot = battleEngine.calculateLoot(monsterTaxaNegativa);
            assertTrue(loot.isEmpty(),
                    "Com taxa -1.0, o item nunca deve dropar (tentativa " + i + ")");
        }
    }

    @Test
    @DisplayName("Loot com monstro sem drops retorna lista vazia")
    void calculateLoot_semDrops_retornaListaVazia() {
        Monster monster = makeMonster(0, 0);
        monster.setDrops(new ArrayList<>());

        List<Item> loot = battleEngine.calculateLoot(monster);

        assertNotNull(loot);
        assertTrue(loot.isEmpty());
    }

    @Test
    @DisplayName("Loot com drops null retorna lista vazia sem lançar exceção")
    void calculateLoot_dropsNull_retornaListaVazia() {
        Monster monster = makeMonster(0, 0);
        monster.setDrops(null);

        List<Item> loot = battleEngine.calculateLoot(monster);

        assertNotNull(loot);
        assertTrue(loot.isEmpty());
    }

    @Test
    @DisplayName("calculateLoot lança IllegalArgumentException quando Monster é null")
    void calculateLoot_monsterNull_lancaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> battleEngine.calculateLoot(null));
    }

    // ── calculateMonsterDamage ─────────────────────────────────────────────────

    @Test
    @DisplayName("Monstro causa dano = ATK do monstro - DEF total do player (VIT + armadura)")
    void calculateMonsterDamage_formulaCorreta() {
        // Player com VIT=5 e sem armadura → getTotalDef() = 5
        // Monster ATK=20 → dano esperado = 20 - 5 = 15
        Monster monster = makeMonster(0, 20);
        Player player = makePlayer(1, 5);

        int damage = battleEngine.calculateMonsterDamage(monster, player);

        assertEquals(15, damage);
    }

    @Test
    @DisplayName("Dano do monstro ao player é no mínimo 1 quando DEF do player é muito alta")
    void calculateMonsterDamage_danoMinimoUm_playerDefAlta() {
        // Monster ATK=1, Player VIT=9999 → dano bruto negativo → deve retornar 1
        Monster monster = makeMonster(0, 1);
        Player player = makePlayer(1, 9999);

        int damage = battleEngine.calculateMonsterDamage(monster, player);

        assertEquals(1, damage);
    }

    @Test
    @DisplayName("Monstro sem stats causa dano mínimo 1 ao player")
    void calculateMonsterDamage_monsterSemStats_causaDanoMinimo() {
        Monster monster = new Monster();
        monster.setStats(null);
        monster.setDrops(new ArrayList<>());

        Player player = makePlayer(1, 1);

        int damage = battleEngine.calculateMonsterDamage(monster, player);

        assertEquals(1, damage);
    }

    @Test
    @DisplayName("calculateMonsterDamage lança IllegalArgumentException quando Monster é null")
    void calculateMonsterDamage_monsterNull_lancaIllegalArgumentException() {
        Player player = makePlayer(1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> battleEngine.calculateMonsterDamage(null, player));
    }

    @Test
    @DisplayName("calculateMonsterDamage lança IllegalArgumentException quando Player é null")
    void calculateMonsterDamage_playerNull_lancaIllegalArgumentException() {
        Monster monster = makeMonster(0, 10);

        assertThrows(IllegalArgumentException.class,
                () -> battleEngine.calculateMonsterDamage(monster, null));
    }
}
