package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The value after a flag: completed by the flag's own type, cleared by a dash, kept whole when it has spaces. */
class ArgParserFlagValueTest extends RegionCommandFixture {

    private FinalCMDPluginCommand fp;
    private Region arena;

    @BeforeEach
    void mount() {
        fp = harness.register(new Fp_Cmd(protNode()));
        arena = prot.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
        steve.grant("fp.region");
    }

    @Test
    void valueTabDelegatesToTheFlagType() {
        List<String> options = harness.tab(fp, steve, "region", "flag", "arena", "deny-pvp", "");

        assertTrue(options.containsAll(Arrays.asList("true", "false", "undefined", "-")), options.toString());
        assertTrue(harness.tab(fp, steve, "region", "group", "arena", "membros", "flag", "deny-pvp", "").contains("true"));
        assertTrue(harness.tab(fp, steve, "region", "settings", "arena", "deny-entry", "").contains("revert="));
        assertEquals(Arrays.asList("revert=trail:"), harness.tab(fp, steve, "region", "settings", "arena", "deny-entry", "revert=tr"));
    }

    @Test
    void dashClearsTheFlag() {
        harness.dispatch(fp, steve, "region flag arena deny-pvp true");
        assertEquals(Tristate.TRUE, valueOf(arena, "deny-pvp"));

        harness.dispatch(fp, steve, "region flag arena deny-pvp -");

        assertNull(valueOf(arena, "deny-pvp"));
        steve.assertAnyMessageContains("cleared");
    }

    @Test
    void compositeValueKeepsSpaces() {
        harness.dispatch(fp, steve, "region flag arena deny-entry-with-message denied=true message=\"keep out\"");

        assertEquals(new DenyEntryMessage(Tristate.TRUE, "keep out"), valueOf(arena, "deny-entry-with-message"), steve.getMessages().toString());
    }
}
