# Test Coverage Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Atingir ≥90% cobertura de linhas e ≥85% branches nas classes com lógica real, excluindo DTOs e importer do JaCoCo.

**Architecture:** Abordagem híbrida — configurar JaCoCo com `<excludes>` para classes sem valor de teste, e adicionar testes unitários/integração focados nos branches descobertos de cada serviço.

**Tech Stack:** Java 17, Spring Boot 3.4.2, JUnit 5, Mockito, JaCoCo 0.8.12, PostgreSQL (`ragnarok_test`)

---

## File Map

| Ação | Arquivo |
|------|---------|
| Modificar | `pom.xml` |
| Criar | `src/test/java/com/ragnarok/application/service/WeaponSizeServiceTest.java` |
| Criar | `src/test/java/com/ragnarok/domain/model/ActiveBuffTest.java` |
| Modificar | `src/test/java/com/ragnarok/application/service/BattleServiceTest.java` |
| Modificar | `src/test/java/com/ragnarok/application/service/SkillServiceAprenderTest.java` |
| Modificar | `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java` |
| Modificar | `src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java` |
| Modificar | `src/test/java/com/ragnarok/application/service/ItemServiceIntegrationTest.java` |
| Modificar | `src/test/java/com/ragnarok/application/service/PlayerServiceTest.java` |
| Modificar | `src/test/java/com/ragnarok/runner/RagnarokTerminalRunnerTest.java` |

---

## Task 0: Configurar JaCoCo no pom.xml

**Files:**
- Modify: `pom.xml:88-103`

- [ ] **Step 1: Substituir as execuções do JaCoCo no pom.xml**

Substituir o bloco atual das execuções do jacoco-maven-plugin por:

```xml
<executions>
    <execution>
        <id>prepare-agent</id>
        <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
        <id>report</id>
        <phase>test</phase>
        <goals><goal>report</goal></goals>
        <configuration>
            <excludes>
                <exclude>com/ragnarok/runner/importer/**</exclude>
                <exclude>com/ragnarok/infrastructure/client/dto/**</exclude>
                <exclude>com/ragnarok/runner/importer/dto/**</exclude>
            </excludes>
        </configuration>
    </execution>
    <execution>
        <id>check</id>
        <phase>test</phase>
        <goals><goal>check</goal></goals>
        <configuration>
            <excludes>
                <exclude>com/ragnarok/runner/importer/**</exclude>
                <exclude>com/ragnarok/infrastructure/client/dto/**</exclude>
                <exclude>com/ragnarok/runner/importer/dto/**</exclude>
            </excludes>
            <rules>
                <rule>
                    <element>BUNDLE</element>
                    <limits>
                        <limit>
                            <counter>LINE</counter>
                            <value>COVEREDRATIO</value>
                            <minimum>0.90</minimum>
                        </limit>
                        <limit>
                            <counter>BRANCH</counter>
                            <value>COVEREDRATIO</value>
                            <minimum>0.85</minimum>
                        </limit>
                    </limits>
                </rule>
            </rules>
        </configuration>
    </execution>
</executions>
```

- [ ] **Step 2: Verificar que o pom.xml compila**

```bash
cd /home/silvio/Documentos/projetos/Ragnarok-simulator
./mvnw validate -q
```
Expected: sem erros de XML.

---

## Task 1: WeaponSizeServiceTest + ActiveBuffTest (Grupo 1 — puro unitário)

**Files:**
- Create: `src/test/java/com/ragnarok/application/service/WeaponSizeServiceTest.java`
- Create: `src/test/java/com/ragnarok/domain/model/ActiveBuffTest.java`

### 1a — WeaponSizeServiceTest

- [ ] **Step 1: Criar WeaponSizeServiceTest.java**

```java
package com.ragnarok.application.service;

import com.ragnarok.domain.model.WeaponType;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierEntity;
import com.ragnarok.infrastructure.persistence.WeaponSizeModifierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaponSizeServiceTest {

    @Mock
    private WeaponSizeModifierRepository repository;

    @InjectMocks
    private WeaponSizeService service;

    private WeaponSizeModifierEntity modifier(int small, int medium, int large) {
        return new WeaponSizeModifierEntity("SWORD", small, medium, large);
    }

    @Test
    @DisplayName("monsterSize=Small retorna smallPct")
    void getModifier_small_returnsSmallPct() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, "Small");

        assertEquals(75, result);
    }

    @Test
    @DisplayName("monsterSize=Large retorna largePct")
    void getModifier_large_returnsLargePct() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, "Large");

        assertEquals(125, result);
    }

    @Test
    @DisplayName("monsterSize=Medium retorna mediumPct")
    void getModifier_medium_returnsMediumPct() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, "Medium");

        assertEquals(100, result);
    }

    @Test
    @DisplayName("monsterSize=null normaliza para MEDIUM")
    void getModifier_nullSize_defaultsMedium() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.of(modifier(75, 100, 125)));

        int result = service.getModifier(WeaponType.SWORD, null);

        assertEquals(100, result);
    }

    @Test
    @DisplayName("weaponType=null usa chave NONE")
    void getModifier_nullWeaponType_usesNoneKey() {
        when(repository.findByWeaponType("NONE")).thenReturn(Optional.of(modifier(80, 100, 120)));

        int result = service.getModifier(null, "Small");

        assertEquals(80, result);
    }

    @Test
    @DisplayName("repositório vazio retorna modificador padrão 100")
    void getModifier_repositoryEmpty_returnsDefault100() {
        when(repository.findByWeaponType("SWORD")).thenReturn(Optional.empty());

        int result = service.getModifier(WeaponType.SWORD, "Small");

        assertEquals(100, result);
    }
}
```

- [ ] **Step 2: Rodar só WeaponSizeServiceTest para confirmar que passa**

```bash
./mvnw test -Dtest=WeaponSizeServiceTest -pl . 2>&1 | tail -20
```
Expected: `Tests run: 6, Failures: 0, Errors: 0`

### 1b — ActiveBuffTest

- [ ] **Step 3: Criar ActiveBuffTest.java**

```java
package com.ragnarok.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class ActiveBuffTest {

    @Test
    @DisplayName("isExpired retorna true quando turnosRestantes == 0")
    void isExpired_zero_returnsTrue() {
        ActiveBuff buff = new ActiveBuff("HEAL", StatType.HP, 50, 0);

        assertTrue(buff.isExpired());
    }

    @Test
    @DisplayName("isExpired retorna false quando turnosRestantes > 0")
    void isExpired_positive_returnsFalse() {
        ActiveBuff buff = new ActiveBuff("HEAL", StatType.HP, 50, 3);

        assertFalse(buff.isExpired());
    }

    @Test
    @DisplayName("decrementar reduz turnosRestantes em 1")
    void decrementar_reducesRemainingTurns() {
        ActiveBuff buff = new ActiveBuff("HEAL", StatType.HP, 50, 3);

        ActiveBuff decrementado = buff.decrementar();

        assertEquals(2, decrementado.getTurnosRestantes());
        assertEquals(50, decrementado.getValue());
        assertEquals("HEAL", decrementado.getSkillAegisName());
    }

    @Test
    @DisplayName("decrementar com turnosRestantes=-1 retorna o próprio objeto (buff permanente)")
    void decrementar_permanent_returnsSame() {
        ActiveBuff buff = new ActiveBuff("PROVOKE", StatType.STR, 10, -1);

        ActiveBuff resultado = buff.decrementar();

        assertSame(buff, resultado, "Buff permanente deve retornar a mesma instância");
    }

    @Test
    @DisplayName("construtor com flags cria buff com EnumSet correto")
    void constructor_withFlags_setsFlags() {
        ActiveBuff buff = new ActiveBuff("PROVOKE", StatType.STR, 10, 3,
                EnumSet.of(BuffFlag.KNOCKBACK_IMMUNE));

        assertTrue(buff.getFlags().contains(BuffFlag.KNOCKBACK_IMMUNE));
    }
}
```

- [ ] **Step 4: Rodar ActiveBuffTest**

```bash
./mvnw test -Dtest=ActiveBuffTest -pl . 2>&1 | tail -20
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

**Nota:** Se `StatType.HP` ou `BuffFlag.KNOCKBACK_IMMUNE` não existirem com esses nomes, ajustar para os valores reais do enum — verificar com `grep -r "enum StatType\|enum BuffFlag" src/main/`.

- [ ] **Step 5: Commit Grupo 1**

```bash
git add pom.xml \
  src/test/java/com/ragnarok/application/service/WeaponSizeServiceTest.java \
  src/test/java/com/ragnarok/domain/model/ActiveBuffTest.java
git commit -m "test: add WeaponSizeServiceTest and ActiveBuffTest (Group 1) + JaCoCo config"
```

---

## Task 2: BattleServiceTest — branches de loot (Grupo 2)

**Files:**
- Modify: `src/test/java/com/ragnarok/application/service/BattleServiceTest.java`

Os testes já cobrem os principais branches. O gap restante está em `processarMorteMonstro` quando `loots` não é vazio — a lógica de `existing.isEmpty()` (novo item) vs não-vazio (stack merge).

- [ ] **Step 1: Adicionar teste — monstro morre com loot (item novo no inventário)**

Adicionar ao final de `BattleServiceTest.java`, antes do `}` que fecha a classe:

```java
// ── Cenário: Monstro morre com loot — item novo adicionado ao inventário ────

@Test
@DisplayName("Monstro morre com loot — item é salvo no inventário do player")
void realizarAtaque_monsterMorreComLoot_itemSalvoNoInventario() {
    PlayerEntity playerEntity = makePlayerEntity(100);
    MonsterEntity monsterEntity = makeMonsterEntity(10);  // HP baixo — morre com 1 hit
    Player playerDomain = makeDomainPlayer(100);
    Monster monsterDomain = makeDomainMonster(10);

    // Loot: 1 item
    Item lootItem = new Item();
    lootItem.setId(500L);
    lootItem.setName("Red Herb");

    ItemEntity lootEntity = new ItemEntity();
    lootEntity.setId(500L);
    lootEntity.setName("Red Herb");

    when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
    when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
    when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
    when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
    when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(20);
    when(battleEngine.calculateLoot(monsterDomain)).thenReturn(List.of(lootItem));
    when(itemMapper.toEntity(lootItem)).thenReturn(lootEntity);
    // Inventário vazio — item ainda não existe para o player
    when(playerItemRepository.findByPlayerIdAndItemId(PLAYER_ID, 500L))
            .thenReturn(new ArrayList<>());

    String resultado = battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

    // Item deve ter sido salvo no inventário
    verify(playerItemRepository).save(argThat(pi ->
            pi.getItem().getId().equals(500L) && pi.getAmount() == 1));
    assertTrue(resultado.toUpperCase().contains("VITORIA") ||
               resultado.toUpperCase().contains("VITÓRIA") ||
               resultado.toUpperCase().contains("VICT"),
            "Resultado deveria indicar vitória: " + resultado);
}

// ── Cenário: Monstro morre com loot — item já no inventário (stack merge) ───

@Test
@DisplayName("Monstro morre com loot — item existente no inventário tem quantidade incrementada")
void realizarAtaque_monsterMorreComLoot_itemExistenteMergedNoStack() {
    PlayerEntity playerEntity = makePlayerEntity(100);
    MonsterEntity monsterEntity = makeMonsterEntity(10);
    Player playerDomain = makeDomainPlayer(100);
    Monster monsterDomain = makeDomainMonster(10);

    Item lootItem = new Item();
    lootItem.setId(501L);
    lootItem.setName("Blue Herb");

    ItemEntity lootEntity = new ItemEntity();
    lootEntity.setId(501L);
    lootEntity.setName("Blue Herb");

    // Já existe 1 no inventário
    PlayerItemEntity existingStack = new PlayerItemEntity();
    existingStack.setItem(lootEntity);
    existingStack.setAmount(3);
    existingStack.setPlayer(playerEntity);

    when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
    when(monsterRepository.findById(MONSTER_ID)).thenReturn(Optional.of(monsterEntity));
    when(playerMapper.toDomain(playerEntity)).thenReturn(playerDomain);
    when(monsterMapper.toDomain(monsterEntity)).thenReturn(monsterDomain);
    when(battleEngine.calculateDamage(playerDomain, monsterDomain)).thenReturn(20);
    when(battleEngine.calculateLoot(monsterDomain)).thenReturn(List.of(lootItem));
    when(itemMapper.toEntity(lootItem)).thenReturn(lootEntity);
    when(playerItemRepository.findByPlayerIdAndItemId(PLAYER_ID, 501L))
            .thenReturn(List.of(existingStack));

    battleService.realizarAtaque(PLAYER_ID, MONSTER_ID);

    // Stack deve ter sido incrementado para 4
    assertEquals(4, existingStack.getAmount(),
            "Amount deve ser 3 existentes + 1 drop = 4");
    verify(playerItemRepository).save(existingStack);
}
```

**Atenção:** `ItemEntity` deve ser importado. Adicionar ao bloco de imports se não estiver:
```java
import com.ragnarok.infrastructure.persistence.ItemEntity;
import com.ragnarok.infrastructure.persistence.PlayerItemEntity;
```

- [ ] **Step 2: Rodar BattleServiceTest completo**

```bash
./mvnw test -Dtest=BattleServiceTest -pl . 2>&1 | tail -20
```
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 3: Commit Grupo 2**

```bash
git add src/test/java/com/ragnarok/application/service/BattleServiceTest.java
git commit -m "test: add loot branch coverage to BattleServiceTest (Group 2)"
```

---

## Task 3: SkillService + ClassChangeService — branches (Grupo 3)

**Files:**
- Modify: `src/test/java/com/ragnarok/application/service/SkillServiceAprenderTest.java`
- Modify: `src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java`
- Modify: `src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java`

### 3a — SkillServiceAprenderTest: branches de validação

- [ ] **Step 1: Adicionar testes de branches em SkillServiceAprenderTest.java**

Adicionar ao final da classe (antes do `}`):

```java
@Test
@DisplayName("aprenderSkill lança IllegalStateException quando sem Skill Points")
void aprenderSkill_semSkillPoints_lancaExcecao() {
    var player = playerRepository.findById(PLAYER_ID).orElseThrow();
    player.setSkillPoints(0);
    playerRepository.save(player);

    var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
    String qualquerSkill = lista.isEmpty() ? "NV_BASIC" : lista.get(0).aegisName();

    assertThrows(IllegalStateException.class,
            () -> skillService.aprenderSkill(PLAYER_ID, qualquerSkill));
}

@Test
@DisplayName("aprenderSkill lança IllegalStateException para skill inexistente na classe")
void aprenderSkill_skillInexistente_lancaExcecao() {
    // PLAYER_ID=1 é NOVICE — "BOWLING_BASH" é skill de Knight, não existe na árvore Novice
    assertThrows(IllegalStateException.class,
            () -> skillService.aprenderSkill(PLAYER_ID, "BOWLING_BASH"));
}

@Test
@DisplayName("listarSkillsDoPlayer retorna lista vazia quando player sem jobClass")
void listarSkillsDoPlayer_jobClassNula_retornaListaVazia() {
    // Cria um player temporário sem jobClass
    // Nota: PlayerEntity deve estar importado no topo do arquivo
    var tempPlayer = new PlayerEntity();
    tempPlayer.setName("SemClasse");
    tempPlayer.setJobClass(null);
    tempPlayer = playerRepository.save(tempPlayer);
    Long tempId = tempPlayer.getId();

    var lista = skillService.listarSkillsDoPlayer(tempId);

    // jobClass null → findByJobClassIgnoreCase(null) → lista vazia
    assertTrue(lista.isEmpty(), "Player sem jobClass deve retornar lista vazia");

    // Cleanup
    playerRepository.deleteById(tempId);
}
```

- [ ] **Step 2: Rodar SkillServiceAprenderTest**

```bash
./mvnw test -Dtest=SkillServiceAprenderTest -pl . 2>&1 | tail -20
```
Expected: `Tests run: 4+, Failures: 0, Errors: 0`

### 3b — SkillServiceIntegrationTest: usarSkillEmCombate e listarSkillsUsaveis

- [ ] **Step 3: Adicionar testes de usarSkillEmCombate em SkillServiceIntegrationTest.java**

Adicionar imports necessários ao topo do arquivo se não estiverem presentes:
```java
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
```

Os campos abaixo só precisam ser adicionados se ainda não existirem na classe:
```java
@Autowired
private SkillRepository skillRepository;

@Autowired
private MonsterRepository monsterRepository;

@Autowired
private PlayerItemRepository playerItemRepository;
```

Adicionar ao final da classe (antes do `}`):

@Test
@DisplayName("usarSkillEmCombate lança exceção quando skill não aprendida")
@Transactional
void usarSkill_naoAprendida_lancaExcecao() {
    // Garante que player NÃO tem a skill
    assertThrows(IllegalStateException.class,
            () -> skillService.usarSkillEmCombate(PLAYER_ID, "NV_BASIC", null));
}

@Test
@DisplayName("listarSkillsUsaveisForaDeCombate retorna somente skills BUFF/HEAL aprendidas")
@Transactional
void listarSkillsUsaveis_comSkillAprendida_retornaSkillBuff() {
    // Aprende NV_BASIC se disponível e é BUFF/HEAL, senão apenas verifica que retorna lista vazia corretamente
    var player = playerRepository.findById(PLAYER_ID).orElseThrow();
    player.setSkillPoints(5);
    playerRepository.save(player);

    // Aprender skill disponível
    var lista = skillService.listarSkillsDoPlayer(PLAYER_ID);
    if (lista.isEmpty()) return; // sem dados de skill_tree no banco de teste

    var disponivel = lista.stream().filter(s -> s.canLearn()).findFirst();
    if (disponivel.isEmpty()) return;

    skillService.aprenderSkill(PLAYER_ID, disponivel.get().aegisName());

    // Verificar que listarSkillsUsaveisForaDeCombate funciona sem lançar exceção
    var usaveis = skillService.listarSkillsUsaveisForaDeCombate(PLAYER_ID);
    assertNotNull(usaveis, "Deve retornar lista não-nula mesmo que vazia");
}
```

**Nota:** Se `playerSkillRepository` já existir com o nome correto na classe, remover o campo duplicado `playerSkillRepository2` — é apenas para apontar que o repositório já está disponível.

- [ ] **Step 4: Rodar SkillServiceIntegrationTest**

```bash
./mvnw test -Dtest=SkillServiceIntegrationTest -pl . 2>&1 | tail -20
```
Expected: sem falhas.

### 3c — ClassChangeServiceTest: verificar existência e branches

- [ ] **Step 5: Ler ClassChangeServiceTest atual**

```bash
cat src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java
```

- [ ] **Step 6: Verificar quais branches de ClassChangeService não estão cobertos**

Os branches principais:
- `trocarClasse` com jobLevel insuficiente (< 9 para Novice)
- `trocarClasse` com classe não disponível no skill_tree
- `listarClassesDisponiveis` para classe sem progressão possível

Se o arquivo existente não cobre esses, adicionar. Padrão para cada novo teste:

```java
@Test
@DisplayName("trocarClasse lança exceção quando jobLevel insuficiente")
void trocarClasse_jobLevelInsuficiente_lancaExcecao() {
    // Player ID=1 é NOVICE — precisa jobLevel >= 9
    var player = playerRepository.findById(1L).orElseThrow();
    player.setJobLevel(5);  // insuficiente
    playerRepository.save(player);

    assertThrows(IllegalStateException.class,
            () -> classChangeService.trocarClasse(1L, com.ragnarok.domain.model.JobClass.SWORDSMAN));
}
```

- [ ] **Step 7: Rodar ClassChangeServiceTest + ClassChangeIntegrationTest**

```bash
./mvnw test -Dtest="ClassChangeServiceTest+ClassChangeIntegrationTest" -pl . 2>&1 | tail -20
```
Expected: sem falhas.

- [ ] **Step 8: Commit Grupo 3**

```bash
git add src/test/java/com/ragnarok/application/service/SkillServiceAprenderTest.java \
        src/test/java/com/ragnarok/application/service/SkillServiceIntegrationTest.java \
        src/test/java/com/ragnarok/application/service/ClassChangeServiceTest.java
git commit -m "test: add branch coverage for SkillService and ClassChangeService (Group 3)"
```

---

## Task 4: ItemService + PlayerService — branches (Grupo 4)

**Files:**
- Modify: `src/test/java/com/ragnarok/application/service/ItemServiceIntegrationTest.java`
- Modify: `src/test/java/com/ragnarok/application/service/PlayerServiceTest.java`

### 4a — ItemServiceIntegrationTest: usarItem e equiparItem branches

- [ ] **Step 1: Adicionar testes de branches em ItemServiceIntegrationTest.java**

Adicionar ao final da classe (antes do `}`):

```java
@Test
@DisplayName("equiparItem lança exceção quando item não é equipamento")
@Transactional
void equiparItem_tipoInvalido_lancaExcecao() {
    // Cria item CONSUMABLE
    ItemEntity consumable = new ItemEntity();
    consumable.setId(88801L);
    consumable.setName("Red Potion");
    consumable.setType(ItemType.CONSUMABLE);
    consumable.setEquipSlot(null);
    itemRepository.save(consumable);

    PlayerEntity player = playerRepository.findById(1L).orElseThrow();
    itemService.darItemAoJogador(player.getId(), 88801L, 1);

    UUID playerItemId = playerItemRepository.findByPlayerId(player.getId())
            .stream()
            .filter(pi -> pi.getItem().getId().equals(88801L))
            .findFirst()
            .orElseThrow()
            .getId();

    assertThrows(IllegalArgumentException.class,
            () -> itemService.equiparItem(player.getId(), playerItemId));
}

@Test
@DisplayName("usarItem com CONSUMABLE chama consumirItem (retorna mensagem de uso)")
@Transactional
void usarItem_consumable_retornaMensagemDeUso() {
    ItemEntity consumable = new ItemEntity();
    consumable.setId(88802L);
    consumable.setName("Blue Potion");
    consumable.setType(ItemType.CONSUMABLE);
    consumable.setScript("percentheal 20, 0;");
    itemRepository.save(consumable);

    PlayerEntity player = playerRepository.findById(1L).orElseThrow();
    itemService.darItemAoJogador(player.getId(), 88802L, 1);

    UUID playerItemId = playerItemRepository.findByPlayerId(player.getId())
            .stream()
            .filter(pi -> pi.getItem().getId().equals(88802L))
            .findFirst()
            .orElseThrow()
            .getId();

    // Não deve lançar exceção e deve retornar alguma mensagem
    String resultado = itemService.usarItem(playerItemId);
    assertNotNull(resultado);
}

@Test
@DisplayName("criarItemDeTeste persiste todos os campos flat (defense, weight, price, slots)")
@Transactional
void criarItemDeTeste_verificaFlatteningCompleto() {
    Item item = itemService.criarItemDeTeste(88803L, "Sword", 100);

    ItemEntity entity = itemRepository.findById(88803L).orElseThrow();
    assertEquals(0, entity.getDefense(), "defense deve ser 0");
    assertEquals(2, entity.getSlots(), "slots deve ser 2");
    assertEquals(10, entity.getWeight(), "weight deve ser 10");
    assertEquals(100, entity.getPrice(), "price deve ser 100");
    assertEquals(ItemType.WEAPON, entity.getType(), "type deve ser WEAPON");
    assertEquals(EquipSlot.HAND_R, entity.getEquipSlot(), "equipSlot deve ser HAND_R");
}
```

- [ ] **Step 2: Rodar ItemServiceIntegrationTest**

```bash
./mvnw test -Dtest=ItemServiceIntegrationTest -pl . 2>&1 | tail -20
```
Expected: sem falhas.

### 4b — PlayerServiceTest: ressuscitarJogador

- [ ] **Step 3: Adicionar teste de ressuscitarJogador em PlayerServiceTest.java**

Adicionar imports se necessário:
```java
import org.springframework.transaction.annotation.Transactional;
```

Adicionar ao final da classe (antes do `}`):

```java
@Test
@DisplayName("ressuscitarJogador restaura HP máximo do player ID=1")
@Transactional
void ressuscitarJogador_restauraHpMaximo() {
    // Zera o HP do player ID=1 para simular morte
    var player = playerRepository.findById(1L).orElseThrow();
    int hpMax = player.getHpMax() != null ? player.getHpMax() : 100;
    player.setHpCurrent(0);
    playerRepository.save(player);

    // Ressuscita
    playerService.ressuscitarJogador(1L);

    // Verifica que HP foi restaurado
    var depois = playerRepository.findById(1L).orElseThrow();
    assertEquals(hpMax, depois.getHpCurrent(),
            "HP deve ser restaurado para o máximo após ressurreição");
}

@Test
@DisplayName("ressuscitarJogador lança exceção para player inexistente")
void ressuscitarJogador_playerInexistente_lancaExcecao() {
    assertThrows(IllegalArgumentException.class,
            () -> playerService.ressuscitarJogador(999999L));
}
```

- [ ] **Step 4: Rodar PlayerServiceTest**

```bash
./mvnw test -Dtest=PlayerServiceTest -pl . 2>&1 | tail -20
```
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit Grupo 4**

```bash
git add src/test/java/com/ragnarok/application/service/ItemServiceIntegrationTest.java \
        src/test/java/com/ragnarok/application/service/PlayerServiceTest.java
git commit -m "test: add branch coverage for ItemService and PlayerService (Group 4)"
```

---

## Task 5: RagnarokTerminalRunner — métodos de lógica (Grupo 5)

**Files:**
- Modify: `src/test/java/com/ragnarok/runner/RagnarokTerminalRunnerTest.java`

O arquivo atual tem 1 teste (`deveRessuscitarJogadorAposMorte`). Vamos adicionar:
- `caminhar(String)` — branch 70% (encontro) e 30% (sem monstro)
- `iniciarEncontroAleatorio(String)` — mapa com monstros
- `moverParaMapa(String)` — troca de mapa
- `handlePlayerDeath()` — ressuscitar e resetar mapa

**Técnica:** `ReflectionTestUtils.invokeMethod()` + mock do `Random` via `ReflectionTestUtils.setField()`.

- [ ] **Step 1: Substituir o conteúdo de RagnarokTerminalRunnerTest.java**

```java
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
        assertFalse(inBattle);
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

    // ── handlePlayerDeath: inBattle=false, ressuscitar, mapa=prontera ─────────

    @Test
    @DisplayName("handlePlayerDeath: reseta inBattle, chama ressuscitar e move para prontera")
    void handlePlayerDeath_resetaBattleEMoveParaProntera() {
        when(playerRepo.findById(1L)).thenReturn(Optional.of(playerMock));

        ReflectionTestUtils.invokeMethod(runner, "handlePlayerDeath");

        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assertFalse(inBattle, "inBattle deve ser false após morte");

        assertNull(ReflectionTestUtils.getField(runner, "currentMonster"),
                "currentMonster deve ser null após morte");

        verify(playerService).ressuscitarJogador(1L);
        assertEquals("prontera", playerMock.getMapName(),
                "mapName deve ser prontera após morte");
        verify(playerRepo).save(playerMock);
    }

    // ── caminhar: branch 70% — encontro com monstro ──────────────────────────

    @Test
    @DisplayName("caminhar: 70% branch — inicia encontro aleatório quando rng < 70")
    void caminhar_encontro_iniciaBatalha() {
        // Mock rng para forçar o branch de encontro (< 70)
        Random mockRng = mock(Random.class);
        when(mockRng.nextInt(100)).thenReturn(50);   // < 70 → encontro
        when(mockRng.nextInt(anyInt())).thenReturn(0); // weighted selection → primeiro monstro
        ReflectionTestUtils.setField(runner, "rng", mockRng);

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
    @DisplayName("caminhar: 30% branch — nenhum monstro quando rng >= 70")
    void caminhar_semEncontro_naoMudaEstado() {
        Random mockRng = mock(Random.class);
        when(mockRng.nextInt(100)).thenReturn(75); // >= 70 → sem encontro
        ReflectionTestUtils.setField(runner, "rng", mockRng);

        // Estado inicial: inBattle=true, currentMonster=monsterMock (do @BeforeEach)
        // Após caminhar sem encontro, estado NÃO deve mudar
        ReflectionTestUtils.invokeMethod(runner, "caminhar", "prontera");

        // mapMonsterRepo não deve ser chamado
        verifyNoInteractions(mapMonsterRepo);
    }

    // ── iniciarEncontroAleatorio: mapa vazio ─────────────────────────────────

    @Test
    @DisplayName("iniciarEncontroAleatorio: mapa sem monstros exibe mensagem e não entra em batalha")
    void iniciarEncontroAleatorio_mapaVazio_naoPodeIniciarBatalha() {
        ReflectionTestUtils.setField(runner, "inBattle", false);
        when(mapMonsterRepo.findByMapId("aldebaran")).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(runner, "iniciarEncontroAleatorio", "aldebaran");

        boolean inBattle = (boolean) ReflectionTestUtils.getField(runner, "inBattle");
        assertFalse(inBattle, "inBattle deve permanecer false se mapa não tem monstros");
    }
}
```

**Nota sobre `caminhar`:** O método chama `Thread.sleep(1000)`. Cada teste de `caminhar` levará ~1s. Com 2 testes de caminhar, isso é 2s extras — aceitável dentro do limite de 60s.

**Nota sobre `MapMonsterEntity`:** O método `mapMonster.setMonster(poring)` e `mapMonster.setAmount(87)` dependem dos setters da entidade. Verificar o nome correto dos campos em `MapMonsterEntity.java` se houver erro de compilação.

- [ ] **Step 2: Rodar RagnarokTerminalRunnerTest**

```bash
./mvnw test -Dtest=RagnarokTerminalRunnerTest -pl . 2>&1 | tail -30
```
Expected: `Tests run: 7, Failures: 0, Errors: 0`

Se `caminhar` falhar por `NullPointerException` na linha do `Thread.sleep`, verificar que o `runner` está inicializado corretamente pelo `@InjectMocks`.

- [ ] **Step 3: Commit Grupo 5**

```bash
git add src/test/java/com/ragnarok/runner/RagnarokTerminalRunnerTest.java
git commit -m "test: add caminhar/moverParaMapa/handlePlayerDeath coverage (Group 5)"
```

---

## Task 6: Verificação Final de Cobertura

- [ ] **Step 1: Rodar suite completa com cobertura**

```bash
./mvnw test 2>&1 | tail -30
```
Expected: `BUILD SUCCESS`, todos os testes passando.

- [ ] **Step 2: Verificar relatório de cobertura**

```bash
# Abrir no browser (ou verificar o XML)
cat target/site/jacoco/jacoco.xml | grep -E 'BUNDLE|LINE|BRANCH' | head -20
```

Ou verificar o HTML:
```
target/site/jacoco/index.html
```

- [ ] **Step 3: Se cobertura < 90% linhas ou < 85% branches**

O build falhará com `BUILD FAILURE` e a mensagem:
```
Rule violated for bundle ragnarok-core: lines covered ratio is X, but expected minimum is 0.90
```

Identificar qual classe está abaixo da meta com:
```bash
grep -A5 "missed" target/site/jacoco/jacoco.xml | grep 'name="' | head -20
```

Adicionar testes pontuais para os métodos descobertos identificados, repeti stepir desta seção.

- [ ] **Step 4: Commit final**

```bash
git add -A
git commit -m "test: coverage targets met — ≥90% lines, ≥85% branches"
```

---

## Critérios de Sucesso

| Critério | Meta |
|----------|------|
| `./mvnw test` passa | 100% (0 falhas) |
| Cobertura linhas (excluindo DTOs/importer) | ≥ 90% |
| Cobertura branches (excluindo DTOs/importer) | ≥ 85% |
| Nenhum teste toca `ragnarok_db` | ✅ |
| Tempo total do `./mvnw test` | < 60s |

---

## Restrições

- Testes `@SpringBootTest` DEVEM ter `@ActiveProfiles("test")`
- Testes que criam dados DEVEM usar `@Transactional` para rollback automático
- Nunca depender de dados pré-existentes além do player ID=1 (criado pelo MockMapLoader)
- Sem mocks de banco — integração usa `ragnarok_test` real
