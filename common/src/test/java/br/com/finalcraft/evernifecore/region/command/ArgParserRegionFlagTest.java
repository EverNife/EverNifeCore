package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flag named on a command line: by its full key, or by a name only one registered flag has. */
class ArgParserRegionFlagTest extends RegionCommandFixture {

    static final List<RegionFlag<?>> SEEN = new ArrayList<>();

    @FinalCMD(aliases = "flagprobe")
    public static class FlagProbe_Cmd {
        @FinalCMD.SubCMD(subcmd = "get")
        public void get(FCommandSender sender, @Arg("<flag>") RegionFlag<?> flag) {
            SEEN.add(flag);
        }
    }

    @BeforeEach
    void clear() {
        SEEN.clear();
    }

    @Test
    void flagResolvesByFullKeyAndByUniqueName() {
        FinalCMDPluginCommand probe = harness.register(new FlagProbe_Cmd());
        RegionFlag<?> denyPvp = flag("deny-pvp");

        harness.dispatch(probe, Senders.console(), "get deny-pvp");
        harness.dispatch(probe, Senders.console(), "get " + denyPvp.getKey());

        assertEquals(Arrays.asList(denyPvp, denyPvp), SEEN);
        assertTrue(harness.tab(probe, Senders.console(), "get", "deny-p").containsAll(Arrays.asList("deny-pvp")));
    }

    @Test
    void unknownFlagIsUnrecognized() {
        FinalCMDPluginCommand probe = harness.register(new FlagProbe_Cmd());
        TestCommandSender console = Senders.console();

        harness.dispatch(probe, console, "get pvp");

        assertTrue(SEEN.isEmpty());
        console.assertAnyMessageContains("There is no flag called");
        console.assertAnyMessageContains(flag("deny-pvp").getKey().toString());   //the close match is suggested
    }
}
