package com.ragnarok.domain.service;

import com.ragnarok.domain.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BattleEngine {

    public int calculateDamage(Player attacker, Monster target) {
        if (attacker == null || target == null) {
            throw new IllegalArgumentException("Attacker and Target cannot be null");
        }

        // 1. Em vez de calcular bônus aqui, apenas pedimos o Total ao Player
        // Usaremos o método getTotalStr() que implementamos no passo anterior
        int totalStr = attacker.getTotalStr();

        // 2. O Status ATK agora usa a Força Total (Base + Itens)
        int statusAtk = totalStr * 2;

        // 3. Weapon ATK (Aqui a Engine ainda olha para a arma para saber o dano base dela)
        int weaponAtk = 0;

        // Filtramos o inventário apenas para pegar o dano base da arma equipada
        for (PlayerItem equip : attacker.getEquipments()) {
            if (equip.getItemDefinition() != null && equip.getItemDefinition().getStats() != null) {
                Integer wAtk = equip.getItemDefinition().getStats().getAttack();
                weaponAtk += (wAtk != null) ? wAtk : 0;
            }
        }

        // 4. Cálculo Final mais limpo
        int totalAtk = statusAtk + weaponAtk;

        int targetDef = target.getStats() != null ? target.getStats().getDef() : 0;
        int damage = totalAtk - targetDef;

        return Math.max(1, damage);
    }

    public List<Item> calculateLoot(Monster monster) {
        if (monster == null) throw new IllegalArgumentException("Monster cannot be null for loot calculation");
        List<Item> droppedItems = new ArrayList<>();

        if (monster.getDrops() == null) return droppedItems;

        for (MonsterDrop drop : monster.getDrops()) {
            // Gera um número entre 0.0 e 100.0
            double roll = ThreadLocalRandom.current().nextDouble(0, 100);

            // Se o roll for menor que a taxa, o item cai
            if (roll <= drop.getRate()) {
                droppedItems.add(drop.getItem());
            }
        }
        return droppedItems;
    }

    public int calculateMonsterDamage(Monster attacker, Player target) {
        if (attacker == null || target == null) {
            throw new IllegalArgumentException("Cannot calculate damage with null Attacker or Target");
        }

        // Fórmula: Ataque do Monstro - (Defesa do Player + Vitalidade)
        int monsterAtk = attacker.getStats() != null ? attacker.getStats().getAttack() : 0;

        int playerDef = (target.getStats() != null && target.getStats().getVit() != null) ? target.getStats().getVit() : 0;

        int damage = monsterAtk - playerDef;
        return Math.max(1, damage); // Dano nunca pode ser negativo ou zero
    }
}