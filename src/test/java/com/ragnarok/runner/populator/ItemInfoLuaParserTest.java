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
}
