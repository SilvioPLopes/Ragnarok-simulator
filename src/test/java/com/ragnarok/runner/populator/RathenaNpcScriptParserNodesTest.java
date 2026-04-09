package com.ragnarok.runner.populator;

import com.ragnarok.domain.model.NpcDialogNode;
import com.ragnarok.runner.populator.dto.RathenaNpcScriptData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RathenaNpcScriptParserNodesTest {

    private final RathenaNpcScriptParser parser = new RathenaNpcScriptParser();

    @Test
    void linearDialog_producesDialogAndCloseNodes(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("t.txt");
        Files.writeString(file, """
                prontera,100,200,0\tscript\tGuard#lbl\t50,{
                \tmes "[Guard]";
                \tmes "Welcome!";
                \tnext;
                \tmes "[Guard]";
                \tmes "Stay safe.";
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);

        assertThat(result).hasSize(1);
        List<NpcDialogNode> nodes = result.get(0).nodes();

        // Node 0: DialogNode "Welcome!"
        assertThat(nodes.get(0)).isInstanceOf(NpcDialogNode.DialogNode.class);
        NpcDialogNode.DialogNode first = (NpcDialogNode.DialogNode) nodes.get(0);
        assertThat(first.speaker()).isEqualTo("Guard");
        assertThat(first.texts()).containsExactly("Welcome!");
        assertThat(first.next()).isEqualTo(1);

        // Node 1: DialogNode "Stay safe."
        NpcDialogNode.DialogNode second = (NpcDialogNode.DialogNode) nodes.get(1);
        assertThat(second.speaker()).isEqualTo("Guard");
        assertThat(second.texts()).containsExactly("Stay safe.");
        assertThat(second.next()).isEqualTo(2);

        // Node 2: ActionNode close
        NpcDialogNode.ActionNode close = (NpcDialogNode.ActionNode) nodes.get(2);
        assertThat(close.action()).isEqualTo("close");
    }

    @Test
    void switchSelect_producesMenuNodeWithChoices(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("t.txt");
        Files.writeString(file, """
                prontera,10,20,0\tscript\tMerchant#lbl\t60,{
                \tmes "[Merchant]";
                \tmes "What do you need?";
                \tnext;
                \tswitch(select("Info:Farewell")) {
                \tcase 1:
                \t\tmes "[Merchant]";
                \t\tmes "We sell potions.";
                \t\tclose;
                \tcase 2:
                \t\tclose;
                \t}
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);
        List<NpcDialogNode> nodes = result.get(0).nodes();

        // Node 0: intro dialog → next=1
        NpcDialogNode.DialogNode intro = (NpcDialogNode.DialogNode) nodes.get(0);
        assertThat(intro.texts()).containsExactly("What do you need?");
        assertThat(intro.next()).isEqualTo(1);

        // Node 1: MenuNode with 2 choices
        NpcDialogNode.MenuNode menu = (NpcDialogNode.MenuNode) nodes.get(1);
        assertThat(menu.choices()).hasSize(2);
        assertThat(menu.choices().get(0).label()).isEqualTo("Info");
        assertThat(menu.choices().get(1).label()).isEqualTo("Farewell");

        // Choice "Info" → some DialogNode with "We sell potions."
        int infoIdx = Integer.parseInt(menu.choices().get(0).next());
        NpcDialogNode.DialogNode infoNode = (NpcDialogNode.DialogNode) nodes.get(infoIdx);
        assertThat(infoNode.texts()).containsExactly("We sell potions.");

        // Choice "Farewell" → ActionNode close
        int farewellIdx = Integer.parseInt(menu.choices().get(1).next());
        NpcDialogNode.ActionNode farewellNode = (NpcDialogNode.ActionNode) nodes.get(farewellIdx);
        assertThat(farewellNode.action()).isEqualTo("close");
    }

    @Test
    void implicitClose_addedWhenScriptEndsWithoutClose(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("t.txt");
        Files.writeString(file, """
                prontera,5,5,0\tscript\tNpc#lbl\t99,{
                \tmes "Hello.";
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);
        List<NpcDialogNode> nodes = result.get(0).nodes();

        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(1)).isInstanceOf(NpcDialogNode.ActionNode.class);
        assertThat(((NpcDialogNode.ActionNode) nodes.get(1)).action()).isEqualTo("close");
    }

    @Test
    void colorCodesRemovedFromTexts(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("t.txt");
        Files.writeString(file, """
                prontera,1,1,0\tscript\tNpc#lbl\t50,{
                \tmes "Hello ^000077world^000000!";
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);
        NpcDialogNode.DialogNode node = (NpcDialogNode.DialogNode) result.get(0).nodes().get(0);

        assertThat(node.texts()).containsExactly("Hello world!");
    }

    @Test
    void nodesFieldIsEmptyWhenNoMes(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("t.txt");
        Files.writeString(file, """
                prontera,5,5,0\tscript\tSilent#lbl\t99,{
                \tclose;
                }
                """, StandardCharsets.ISO_8859_1);

        List<RathenaNpcScriptData> result = parser.parseFile(file);
        assertThat(result.get(0).nodes()).isEmpty();
    }
}
