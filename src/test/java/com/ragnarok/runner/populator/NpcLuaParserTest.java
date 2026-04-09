package com.ragnarok.runner.populator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NpcLuaParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesJobIdsFromUtf16LeFile() throws IOException {
        String content = "jobtbl = {\n" +
                "  JT_WARPNPC = 45,\n" +
                "  JT_1_F_01 = 66,\n" +
                "  JT_1_F_MERCHANT_01 = 73,\n" +
                "  JT_4_F_KAFRA1 = 117,\n" +
                "  JT_MON_BEGIN = 1000,\n" +
                "  JT_PORING = 1002,\n" +
                "}\n";
        Path file = writeUtf16Le(tempDir.resolve("npcidentity.lua"), content);

        Map<String, Integer> result = new NpcLuaParser().parseNpcIdentity(file.toString());

        assertThat(result).containsEntry("JT_WARPNPC", 45);
        assertThat(result).containsEntry("JT_1_F_01", 66);
        assertThat(result).containsEntry("JT_1_F_MERCHANT_01", 73);
        assertThat(result).containsEntry("JT_4_F_KAFRA1", 117);
        assertThat(result).containsEntry("JT_MON_BEGIN", 1000);
        assertThat(result).containsEntry("JT_PORING", 1002);
    }

    @Test
    void ignoresNonAssignmentLines() throws IOException {
        String content = "jobtbl = {\n" +
                "  -- comment\n" +
                "  JT_VALID = 99,\n" +
                "  string_value = \"foo\",\n" +
                "}\n";
        Path file = writeUtf16Le(tempDir.resolve("npcidentity2.lua"), content);

        Map<String, Integer> result = new NpcLuaParser().parseNpcIdentity(file.toString());

        assertThat(result).containsOnlyKeys("JT_VALID");
        assertThat(result).containsEntry("JT_VALID", 99);
    }

    private Path writeUtf16Le(Path path, String content) throws IOException {
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] all = new byte[bom.length + contentBytes.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(contentBytes, 0, all, bom.length, contentBytes.length);
        Files.write(path, all);
        return path;
    }
}
