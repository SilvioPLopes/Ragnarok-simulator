package com.ragnarok.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    // --- Helpers ---

    private Player playerComStats(int str, int agi, int vit, int intVal, int dex, int luk) {
        Player p = new Player();
        p.setStats(new PlayerStats(str, agi, vit, intVal, dex, luk, 100, 40));
        p.setBaseLevel(1);
        p.setInventory(new ArrayList<>());
        p.setActiveBuffs(new ArrayList<>());
        return p;
    }

    private PlayerItem itemEquipado(ItemStats stats) {
        Item item = new Item();
        item.setStats(stats);
        PlayerItem pi = new PlayerItem();
        pi.setItemDefinition(item);
        pi.setIsEquipped(true);
        return pi;
    }

    // ----------------------------------------------------------------
    // getEquipments()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getEquipments retorna lista vazia quando inventário é null")
    void getEquipments_nullInventory_returnsEmpty() {
        Player p = new Player();
        p.setInventory(null);
        assertTrue(p.getEquipments().isEmpty());
    }

    @Test
    @DisplayName("getEquipments retorna apenas itens com isEquipped=true")
    void getEquipments_mixedInventory_returnsOnlyEquipped() {
        Player p = new Player();
        PlayerItem equipped = itemEquipado(new ItemStats());
        PlayerItem notEquipped = new PlayerItem();
        notEquipped.setIsEquipped(false);
        p.setInventory(List.of(equipped, notEquipped));
        assertEquals(1, p.getEquipments().size());
        assertTrue(p.getEquipments().contains(equipped));
    }

    // ----------------------------------------------------------------
    // getBuffBonus()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getBuffBonus retorna 0 quando activeBuffs é null")
    void getBuffBonus_nullBuffs_returnsZero() {
        Player p = new Player();
        p.setActiveBuffs(null);
        assertEquals(0, p.getBuffBonus(StatType.STR));
    }

    @Test
    @DisplayName("getBuffBonus soma valores dos buffs ativos do stat correto")
    void getBuffBonus_activeBuff_returnsValue() {
        Player p = new Player();
        p.setActiveBuffs(List.of(new ActiveBuff("SKILL", StatType.STR, 10, 5)));
        assertEquals(10, p.getBuffBonus(StatType.STR));
    }

    @Test
    @DisplayName("getBuffBonus ignora buffs de stats diferentes")
    void getBuffBonus_differentStat_returnsZero() {
        Player p = new Player();
        p.setActiveBuffs(List.of(new ActiveBuff("SKILL", StatType.AGI, 10, 5)));
        assertEquals(0, p.getBuffBonus(StatType.STR));
    }

    @Test
    @DisplayName("getBuffBonus ignora buffs expirados (turnosRestantes=0)")
    void getBuffBonus_expiredBuff_returnsZero() {
        Player p = new Player();
        // turnosRestantes=0 → isExpired() == true
        p.setActiveBuffs(List.of(new ActiveBuff("SKILL", StatType.STR, 10, 0)));
        assertEquals(0, p.getBuffBonus(StatType.STR));
    }

    // ----------------------------------------------------------------
    // hasBuffFlag()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("hasBuffFlag retorna false quando activeBuffs é null")
    void hasBuffFlag_nullBuffs_returnsFalse() {
        Player p = new Player();
        p.setActiveBuffs(null);
        assertFalse(p.hasBuffFlag(BuffFlag.KNOCKBACK_IMMUNE));
    }

    @Test
    @DisplayName("hasBuffFlag retorna true quando buff ativo tem a flag")
    void hasBuffFlag_withMatchingFlag_returnsTrue() {
        Player p = new Player();
        // @AllArgsConstructor: (aegisName, statType, value, turnosRestantes, flags)
        ActiveBuff buffWithFlag = new ActiveBuff("SKILL", null, 0, 5, EnumSet.of(BuffFlag.KNOCKBACK_IMMUNE));
        p.setActiveBuffs(List.of(buffWithFlag));
        assertTrue(p.hasBuffFlag(BuffFlag.KNOCKBACK_IMMUNE));
    }

    @Test
    @DisplayName("hasBuffFlag retorna false quando buff não tem a flag")
    void hasBuffFlag_buffWithoutFlag_returnsFalse() {
        Player p = new Player();
        // construtor de 4 args cria flags vazio
        p.setActiveBuffs(List.of(new ActiveBuff("SKILL", StatType.STR, 5, 5)));
        assertFalse(p.hasBuffFlag(BuffFlag.KNOCKBACK_IMMUNE));
    }

    // ----------------------------------------------------------------
    // decrementarBuffs() e purgarBuffsExpirados()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("decrementarBuffs com activeBuffs null não lança exceção")
    void decrementarBuffs_nullBuffs_noException() {
        Player p = new Player();
        p.setActiveBuffs(null);
        assertDoesNotThrow(p::decrementarBuffs);
    }

    @Test
    @DisplayName("decrementarBuffs remove buff que expira após decremento (1→0)")
    void decrementarBuffs_removesBuffExpiradoAposDecremento() {
        Player p = new Player();
        p.setActiveBuffs(new ArrayList<>(List.of(new ActiveBuff("SKILL", StatType.STR, 5, 1))));
        p.decrementarBuffs(); // 1 → 0 → isExpired → purgado
        assertTrue(p.getActiveBuffs().isEmpty());
    }

    @Test
    @DisplayName("decrementarBuffs não remove buff permanente (turnosRestantes=-1)")
    void decrementarBuffs_permanentBuff_notRemoved() {
        Player p = new Player();
        p.setActiveBuffs(new ArrayList<>(List.of(new ActiveBuff("PASSIVE", StatType.STR, 3, -1))));
        p.decrementarBuffs();
        assertEquals(1, p.getActiveBuffs().size());
    }

    @Test
    @DisplayName("decrementarBuffs decrementa turnosRestantes de buff ativo")
    void decrementarBuffs_decrementsActiveBuff() {
        Player p = new Player();
        p.setActiveBuffs(new ArrayList<>(List.of(new ActiveBuff("SKILL", StatType.STR, 5, 3))));
        p.decrementarBuffs(); // 3 → 2
        assertEquals(1, p.getActiveBuffs().size());
        assertEquals(2, p.getActiveBuffs().get(0).getTurnosRestantes());
    }

    @Test
    @DisplayName("purgarBuffsExpirados com activeBuffs null não lança exceção")
    void purgarBuffsExpirados_nullBuffs_noException() {
        Player p = new Player();
        p.setActiveBuffs(null);
        assertDoesNotThrow(p::purgarBuffsExpirados);
    }

    @Test
    @DisplayName("purgarBuffsExpirados remove buffs com turnosRestantes=0")
    void purgarBuffsExpirados_removesExpiredBuffs() {
        Player p = new Player();
        ActiveBuff expired = new ActiveBuff("SKILL", StatType.STR, 5, 0);
        ActiveBuff active = new ActiveBuff("SKILL2", StatType.AGI, 3, 2);
        p.setActiveBuffs(new ArrayList<>(List.of(expired, active)));
        p.purgarBuffsExpirados();
        assertEquals(1, p.getActiveBuffs().size());
        assertEquals("SKILL2", p.getActiveBuffs().get(0).getSkillAegisName());
    }

    // ----------------------------------------------------------------
    // getTotalStr()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getTotalStr com inventário null retorna base + buff")
    void getTotalStr_nullInventory_returnsBaseAndBuff() {
        Player p = new Player();
        p.setStats(new PlayerStats(5, 0, 0, 0, 0, 0, 100, 40));
        p.setInventory(null);
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.STR, 3, 5)));
        assertEquals(8, p.getTotalStr());
    }

    @Test
    @DisplayName("getTotalStr soma bonusStr de item equipado")
    void getTotalStr_equippedItemWithBonus_addsBonus() {
        Player p = new Player();
        p.setStats(new PlayerStats(5, 0, 0, 0, 0, 0, 100, 40));
        p.setActiveBuffs(new ArrayList<>());
        ItemStats stats = new ItemStats();
        stats.setBonusStr(3);
        p.setInventory(new ArrayList<>(List.of(itemEquipado(stats))));
        assertEquals(8, p.getTotalStr());
    }

    @Test
    @DisplayName("getTotalStr com itemDefinition null retorna zero do item")
    void getTotalStr_nullItemDefinition_returnsZeroBonus() {
        Player p = new Player();
        p.setStats(new PlayerStats(5, 0, 0, 0, 0, 0, 100, 40));
        p.setActiveBuffs(new ArrayList<>());
        PlayerItem piNullDef = new PlayerItem();
        piNullDef.setItemDefinition(null);
        piNullDef.setIsEquipped(true);
        p.setInventory(List.of(piNullDef));
        assertEquals(5, p.getTotalStr());
    }

    @Test
    @DisplayName("getTotalStr com stats null no item retorna zero do item")
    void getTotalStr_nullItemStats_returnsZeroBonus() {
        Player p = new Player();
        p.setStats(new PlayerStats(5, 0, 0, 0, 0, 0, 100, 40));
        p.setActiveBuffs(new ArrayList<>());
        Item item = new Item();
        item.setStats(null);
        PlayerItem pi = new PlayerItem();
        pi.setItemDefinition(item);
        pi.setIsEquipped(true);
        p.setInventory(List.of(pi));
        assertEquals(5, p.getTotalStr());
    }

    @Test
    @DisplayName("getTotalStr com bonusStr null no stats retorna zero do item")
    void getTotalStr_nullBonusStr_returnsZeroBonus() {
        Player p = new Player();
        p.setStats(new PlayerStats(5, 0, 0, 0, 0, 0, 100, 40));
        p.setActiveBuffs(new ArrayList<>());
        ItemStats stats = new ItemStats();
        stats.setBonusStr(null);
        p.setInventory(List.of(itemEquipado(stats)));
        assertEquals(5, p.getTotalStr());
    }

    // ----------------------------------------------------------------
    // getTotalVit / getTotalInt / getTotalDex / getTotalAgi / getTotalLuk
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getTotalVit inclui base, bonusVit de equip e buff")
    void getTotalVit_includesBaseEquipBuff() {
        Player p = playerComStats(0, 0, 10, 0, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setBonusVit(5);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.VIT, 2, 5)));
        assertEquals(17, p.getTotalVit());
    }

    @Test
    @DisplayName("getTotalInt inclui base, bonusInt de equip e buff")
    void getTotalInt_includesBaseEquipBuff() {
        Player p = playerComStats(0, 0, 0, 10, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setBonusInt(5);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.INT, 2, 5)));
        assertEquals(17, p.getTotalInt());
    }

    @Test
    @DisplayName("getTotalDex inclui base, bonusDex de equip e buff")
    void getTotalDex_includesBaseEquipBuff() {
        Player p = playerComStats(0, 0, 0, 0, 10, 0);
        ItemStats stats = new ItemStats();
        stats.setBonusDex(5);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.DEX, 2, 5)));
        assertEquals(17, p.getTotalDex());
    }

    @Test
    @DisplayName("getTotalAgi inclui base, bonusAgi de equip e buff")
    void getTotalAgi_includesBaseEquipBuff() {
        Player p = playerComStats(0, 10, 0, 0, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setBonusAgi(5);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.AGI, 2, 5)));
        assertEquals(17, p.getTotalAgi());
    }

    @Test
    @DisplayName("getTotalLuk inclui base, bonusLuk de equip e buff")
    void getTotalLuk_includesBaseEquipBuff() {
        Player p = playerComStats(0, 0, 0, 0, 0, 10);
        ItemStats stats = new ItemStats();
        stats.setBonusLuk(5);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.LUK, 2, 5)));
        assertEquals(17, p.getTotalLuk());
    }

    // ----------------------------------------------------------------
    // getTotalAtk() / getTotalMAtk()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getTotalAtk = STR*2 + weapon ATK + ATK buff")
    void getTotalAtk_includesAllComponents() {
        Player p = playerComStats(10, 0, 0, 0, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setAttack(50);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.ATK, 5, 5)));
        assertEquals(75, p.getTotalAtk()); // (10*2) + 50 + 5
    }

    @Test
    @DisplayName("getTotalAtk com item sem attack retorna zero do item")
    void getTotalAtk_nullAttack_returnsZeroFromItem() {
        Player p = playerComStats(5, 0, 0, 0, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setAttack(null);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(new ArrayList<>());
        assertEquals(10, p.getTotalAtk()); // 5*2 + 0 + 0
    }

    @Test
    @DisplayName("getTotalMAtk = INT*2 + weapon mATK")
    void getTotalMAtk_includesIntAndWeapon() {
        Player p = playerComStats(0, 0, 0, 10, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setMAttack(15);
        p.setInventory(List.of(itemEquipado(stats)));
        assertEquals(35, p.getTotalMAtk()); // (10*2) + 15
    }

    @Test
    @DisplayName("getTotalMAtk com mAttack null retorna zero do item")
    void getTotalMAtk_nullMAttack_returnsZeroFromItem() {
        Player p = playerComStats(0, 0, 0, 8, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setMAttack(null);
        p.setInventory(List.of(itemEquipado(stats)));
        assertEquals(16, p.getTotalMAtk()); // 8*2 + 0
    }

    // ----------------------------------------------------------------
    // getTotalDef()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getTotalDef = VIT + armor DEF + DEF buff")
    void getTotalDef_includesVitArmorBuff() {
        Player p = playerComStats(0, 0, 8, 0, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setDefense(5);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.DEF, 2, 5)));
        assertEquals(15, p.getTotalDef()); // 8 + 5 + 2
    }

    @Test
    @DisplayName("getTotalDef com defense null retorna zero do item")
    void getTotalDef_nullDefense_returnsZeroFromItem() {
        Player p = playerComStats(0, 0, 6, 0, 0, 0);
        ItemStats stats = new ItemStats();
        stats.setDefense(null);
        p.setInventory(List.of(itemEquipado(stats)));
        p.setActiveBuffs(new ArrayList<>());
        assertEquals(6, p.getTotalDef()); // 6 + 0 + 0
    }

    // ----------------------------------------------------------------
    // getTotalHit() / getTotalFlee()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getTotalHit = DEX + baseLevel")
    void getTotalHit_equalsDexPlusLevel() {
        Player p = playerComStats(0, 0, 0, 0, 15, 0);
        p.setBaseLevel(5);
        assertEquals(20, p.getTotalHit()); // 15 + 5
    }

    @Test
    @DisplayName("getTotalHit com baseLevel null usa 1")
    void getTotalHit_nullLevel_usesOne() {
        Player p = playerComStats(0, 0, 0, 0, 10, 0);
        p.setBaseLevel(null);
        assertEquals(11, p.getTotalHit()); // 10 + 1
    }

    @Test
    @DisplayName("getTotalFlee = AGI + baseLevel + FLEE buff")
    void getTotalFlee_equalsAgiPlusLevelPlusFlee() {
        Player p = playerComStats(0, 10, 0, 0, 0, 0);
        p.setBaseLevel(3);
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.FLEE, 5, 5)));
        assertEquals(18, p.getTotalFlee()); // 10 + 3 + 5
    }

    // ----------------------------------------------------------------
    // getMaxHp() / getMaxSp()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("getMaxHp com stats null retorna 100")
    void getMaxHp_noStats_returns100() {
        Player p = new Player();
        p.setActiveBuffs(new ArrayList<>());
        assertEquals(100, p.getMaxHp());
    }

    @Test
    @DisplayName("getMaxHp com MAX_HP_PERCENT buff aumenta percentualmente")
    void getMaxHp_withPercentBuff_increases() {
        Player p = new Player();
        p.setStats(new PlayerStats(0, 0, 0, 0, 0, 0, 100, 40));
        p.setActiveBuffs(List.of(new ActiveBuff("S", StatType.MAX_HP_PERCENT, 20, 5)));
        assertEquals(120, p.getMaxHp()); // 100 + 100*20/100
    }

    @Test
    @DisplayName("getMaxSp com stats null retorna 40")
    void getMaxSp_noStats_returns40() {
        Player p = new Player();
        assertEquals(40, p.getMaxSp());
    }

    @Test
    @DisplayName("getMaxSp retorna maxSp do stats quando definido")
    void getMaxSp_withStats_returnsStatValue() {
        Player p = new Player();
        p.setStats(new PlayerStats(0, 0, 0, 0, 0, 0, 200, 60));
        assertEquals(60, p.getMaxSp());
    }
}
