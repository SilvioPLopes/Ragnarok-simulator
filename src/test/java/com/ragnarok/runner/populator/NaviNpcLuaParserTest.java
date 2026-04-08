package com.ragnarok.runner.populator;

import com.ragnarok.runner.populator.dto.NaviNpcData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NaviNpcLuaParserTest {

    private final NaviNpcLuaParser parser = new NaviNpcLuaParser();

    @Test
    void extractsSpriteJobIdFromField3(@TempDir Path tmpDir) throws IOException {
        String content = """
                Navi_Npc = {
                    {
                        "prontera",
                        12345,
                        101,
                        956,
                        "Homem",
                        "King",
                        232,
                        233
                    },
                }
                """;
        Path file = tmpDir.resolve("navi_npc_br.lua");
        Files.write(file, content.getBytes(StandardCharsets.UTF_16LE));

        List<NaviNpcData> result = parser.parse(file.toString());

        assertThat(result).hasSize(1);
        NaviNpcData npc = result.get(0);
        assertThat(npc.spriteJobId()).isEqualTo(956);
        assertThat(npc.mapName()).isEqualTo("prontera");
        assertThat(npc.npcId()).isEqualTo(12345);
        assertThat(npc.name()).isEqualTo("Homem");
        assertThat(npc.spriteClass()).isEqualTo("King");
        assertThat(npc.x()).isEqualTo(232);
        assertThat(npc.y()).isEqualTo(233);
    }
}
