package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.RathenaNpcScriptData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RathenaNpcScriptParserTest {

    private final RathenaNpcScriptParser parser = new RathenaNpcScriptParser();

    @Test
    void parsesSimpleScriptNpc(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("test.txt");
        Files.writeString(file, """
                prontera,101,288,3\tscript\tShuger#pront\t98,{
                \tmes "[Shuger]";
                \tmes "Welcome to Prontera.";
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);

        assertThat(result).hasSize(1);
        RathenaNpcScriptData npc = result.get(0);
        assertThat(npc.mapName()).isEqualTo("prontera");
        assertThat(npc.x()).isEqualTo(101);
        assertThat(npc.y()).isEqualTo(288);
        assertThat(npc.spriteId()).isEqualTo(98);
        assertThat(npc.name()).isEqualTo("Shuger");
        assertThat(npc.dialog()).isEqualTo("[Shuger]\nWelcome to Prontera.");
    }

    @Test
    void removesColorCodesFromDialog(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("color.txt");
        Files.writeString(file, """
                prontera,100,200,0\tscript\tTeste#lbl\t50,{
                \tmes "Hello ^000077world^000000!";
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dialog()).isEqualTo("Hello world!");
    }

    @Test
    void ignoresTemplateWithDashMap(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("template.txt");
        Files.writeString(file, """
                -\tscript\tGuard#pront::anchor\t105,{
                \tmes "I am a template.";
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsNullDialogWhenNoMes(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("nomes.txt");
        Files.writeString(file, """
                prontera,50,50,0\tscript\tSilent#lbl\t99,{
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dialog()).isNull();
    }

    @Test
    void parsesMultipleNpcsInSameFile(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("multi.txt");
        Files.writeString(file, """
                prontera,10,20,0\tscript\tNpc1#lbl1\t60,{
                \tmes "First NPC.";
                \tclose;
                }
                prontera,30,40,0\tscript\tNpc2#lbl2\t61,{
                \tmes "Second NPC.";
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Npc1");
        assertThat(result.get(1).name()).isEqualTo("Npc2");
    }
}
