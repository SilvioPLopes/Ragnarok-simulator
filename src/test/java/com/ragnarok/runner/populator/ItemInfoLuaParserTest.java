package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.ItemClientData;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ItemInfoLuaParserTest {

    @Test
    void parsesItemFields() throws Exception {
        String lua = """
                tbl = {
                [501] = {
                  identifiedDisplayName = "Red Potion",
                  identifiedResourceName = "RED_POTION",
                  identifiedDescriptionName = {
                    "Restores 45 HP.",
                    "Weight: 70"
                  },
                },
                }
                """;
        Path tmp = Files.createTempFile("iteminfo", ".lua");
        Files.writeString(tmp, lua, StandardCharsets.UTF_8);

        Map<Integer, ItemClientData> result = new ItemInfoLuaParser().parse(tmp.toString());

        assertThat(result).containsKey(501);
        assertThat(result.get(501).getDisplayName()).isEqualTo("Red Potion");
        assertThat(result.get(501).getResourceName()).isEqualTo("RED_POTION");
        assertThat(result.get(501).getDescription()).containsExactly("Restores 45 HP.", "Weight: 70");

        Files.delete(tmp);
    }

    @Test
    void parsesItemWithMultipleDescriptionBlocks() throws Exception {
        String lua = """
                tbl = {
                [501] = {
                  unidentifiedDisplayName = "Red Potion",
                  unidentifiedResourceName = "RED_POTION",
                  unidentifiedDescriptionName = {
                    "Unidentified description."
                  },
                  identifiedDisplayName = "Red Potion",
                  identifiedResourceName = "RED_POTION",
                  identifiedDescriptionName = {
                    "Restores 45 HP.",
                    "Weight: 70"
                  },
                  slotCount = 0,
                  ClassNum = 0,
                },
                [502] = {
                  unidentifiedDisplayName = "Orange Potion",
                  unidentifiedResourceName = "ORANGE_POTION",
                  unidentifiedDescriptionName = {
                    "Unidentified."
                  },
                  identifiedDisplayName = "Orange Potion",
                  identifiedResourceName = "ORANGE_POTION",
                  identifiedDescriptionName = {
                    "Restores 200 HP."
                  },
                  slotCount = 0,
                  ClassNum = 0,
                },
                }
                """;
        Path tmp = Files.createTempFile("iteminfo", ".lua");
        Files.writeString(tmp, lua, StandardCharsets.UTF_8);

        Map<Integer, ItemClientData> result = new ItemInfoLuaParser().parse(tmp.toString());

        assertThat(result).hasSize(2);

        assertThat(result.get(501).getDisplayName()).isEqualTo("Red Potion");
        assertThat(result.get(501).getResourceName()).isEqualTo("RED_POTION");
        assertThat(result.get(501).getDescription()).containsExactly("Restores 45 HP.", "Weight: 70");

        assertThat(result.get(502).getDisplayName()).isEqualTo("Orange Potion");
        assertThat(result.get(502).getDescription()).containsExactly("Restores 200 HP.");

        Files.delete(tmp);
    }

    // ── convertLuaEscapes ─────────────────────────────────────────────────────

    @Test
    void convertLuaEscapes_null_returnsNull() {
        assertThat(new ItemInfoLuaParser().convertLuaEscapes(null)).isNull();
    }

    @Test
    void convertLuaEscapes_noEscapes_returnsUnchanged() {
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("Red Potion")).isEqualTo("Red Potion");
    }

    @Test
    void convertLuaEscapes_singleEscape_ferraoDeAbelha() {
        // \227 = 227 decimal = ã (U+00E3)
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("Ferr\\227o")).isEqualTo("Ferrão");
    }

    @Test
    void convertLuaEscapes_multipleEscapes_racaoParaMonstros() {
        // \231 = 231 decimal = ç (U+00E7), \227 = ã
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("Ra\\231\\227o para Monstros"))
                .isEqualTo("Ração para Monstros");
    }

    @Test
    void convertLuaEscapes_escapeAtStart() {
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("\\227bc")).isEqualTo("ãbc");
    }

    @Test
    void convertLuaEscapes_escapeAtEnd() {
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("abc\\227")).isEqualTo("abcã");
    }

    @Test
    void convertLuaEscapes_fourDigits_convertsFirstThreePreservesLast() {
        // \2279 → converts \227 → ã, then '9' stays
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("\\2279")).isEqualTo("ã9");
    }

    @Test
    void convertLuaEscapes_singleDigit_convertsCorrectly() {
        // \2abc → char(2) + "abc"
        assertThat(new ItemInfoLuaParser().convertLuaEscapes("\\2abc")).isEqualTo("\u0002abc");
    }

    // ── removeColorCodes ──────────────────────────────────────────────────────

    @Test
    void removeColorCodes_null_returnsNull() {
        assertThat(new ItemInfoLuaParser().removeColorCodes(null)).isNull();
    }

    @Test
    void removeColorCodes_noCode_returnsUnchanged() {
        assertThat(new ItemInfoLuaParser().removeColorCodes("Recupera HP.")).isEqualTo("Recupera HP.");
    }

    @Test
    void removeColorCodes_singleCode_removedFromText() {
        assertThat(new ItemInfoLuaParser().removeColorCodes("^0000ffRecupera HP.^000000"))
                .isEqualTo("Recupera HP.");
    }

    @Test
    void removeColorCodes_multipleCodes_allRemoved() {
        assertThat(new ItemInfoLuaParser().removeColorCodes("^ff0000Fogo^000000 e ^0000ffGelo^000000"))
                .isEqualTo("Fogo e Gelo");
    }

    @Test
    void removeColorCodes_codeAtStart_removed() {
        assertThat(new ItemInfoLuaParser().removeColorCodes("^0000ffTexto")).isEqualTo("Texto");
    }

    @Test
    void removeColorCodes_codeAtEnd_removed() {
        assertThat(new ItemInfoLuaParser().removeColorCodes("Texto^000000")).isEqualTo("Texto");
    }

    @Test
    void removeColorCodes_codeWithoutFollowingText_returnsEmpty() {
        assertThat(new ItemInfoLuaParser().removeColorCodes("^0000ff")).isEqualTo("");
    }

    // ── integração: parser aplica ambos os métodos ─────────────────────────────

    @Test
    void parser_displayNameWithOctalEscapes_convertedCorrectly() throws Exception {
        String lua = """
                tbl = {
                [616] = {
                  identifiedDisplayName = "Ferr\\227o de Abelha",
                  identifiedResourceName = "Bee_Sting",
                  identifiedDescriptionName = {
                    "^0000ffItem especial.^000000",
                  },
                },
                }
                """;
        Path tmp = Files.createTempFile("iteminfo_escapes", ".lua");
        Files.writeString(tmp, lua, StandardCharsets.UTF_8);

        Map<Integer, ItemClientData> result = new ItemInfoLuaParser().parse(tmp.toString());

        assertThat(result.get(616).getDisplayName()).isEqualTo("Ferrão de Abelha");
        assertThat(result.get(616).getDescription()).containsExactly("Item especial.");

        Files.delete(tmp);
    }

    @Test
    void parser_resourceNameWithOctalEscapes_convertedCorrectly() throws Exception {
        String lua = """
                tbl = {
                [532] = {
                  identifiedDisplayName = "Ra\\231\\227o para Monstros",
                  identifiedResourceName = "Ra\\231\\227o",
                  identifiedDescriptionName = {
                  },
                },
                }
                """;
        Path tmp = Files.createTempFile("iteminfo_racao", ".lua");
        Files.writeString(tmp, lua, StandardCharsets.UTF_8);

        Map<Integer, ItemClientData> result = new ItemInfoLuaParser().parse(tmp.toString());

        assertThat(result.get(532).getDisplayName()).isEqualTo("Ração para Monstros");
        assertThat(result.get(532).getResourceName()).isEqualTo("Ração");

        Files.delete(tmp);
    }
}
