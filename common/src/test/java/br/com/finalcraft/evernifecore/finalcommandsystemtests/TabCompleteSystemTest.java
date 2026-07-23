package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestCommandSender;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code FinalCMDPluginCommand.tabComplete} (matrix E): first-arg sub-command listing
 * (permission/playerOnly/prefix filters), {@link CMDAccessValidation#onPreTabValidation}, and
 * value-position delegation to each {@code ITabParser}.
 */
class TabCompleteSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Tab", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // E1 - first-arg listing: filtered by permission, by playerOnly (console can't see it), and by
    // the typed prefix (startsWithIgnoreCase)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "e1cmd")
    public static class E1_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "show")
        public void show(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "secret", permission = "e1.secret")
        public void secret(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "sonly")
        public void sonly(FPlayer player) {}

        @FinalCMD.SubCMD(subcmd = "other")
        public void other(FCommandSender sender) {}
    }

    @Test
    void e1_firstArgListIsFilteredByPermissionPlayerOnlyAndPrefix() {
        FinalCMDPluginCommand command = newHarness().register(new E1_Cmd());

        TestCommandSender console = new TestCommandSender("console");
        //"other" is excluded by the "s" prefix; "secret" by permission; "sonly" by playerOnly
        assertEquals(List.of("set", "show"), harness.tab(command, console, "s"));

        TestFPlayerSender player = new TestFPlayerSender("Steve");
        //same sender, still no "e1.secret" permission, but now IS a player -> "sonly" shows up
        assertEquals(List.of("set", "show", "sonly"), harness.tab(command, player, "s"));
    }

    // ------------------------------------------------------------------
    // E2 - CMDAccessValidation.onPreTabValidation == false hides the sub-command from the first-arg
    // listing
    // ------------------------------------------------------------------

    public static class HidingValidation extends CMDAccessValidation {
        public HidingValidation() {
        }

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            return true;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            return false;
        }
    }

    @FinalCMD(aliases = "e2cmd")
    public static class E2_Cmd {
        @FinalCMD.SubCMD(subcmd = "hidden", validation = {HidingValidation.class})
        public void hidden(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "visible")
        public void visible(FCommandSender sender) {}
    }

    @Test
    void e2_deniedAccessValidationHidesTheSubCommandFromTab() {
        FinalCMDPluginCommand command = newHarness().register(new E2_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("visible"), harness.tab(command, sender, ""));
    }

    // ------------------------------------------------------------------
    // E3 - index >= 1 delegates to the ITabParser at that position (Boolean's choices)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "e3cmd")
    public static class E3_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Boolean value) {}
    }

    @Test
    void e3_valuePositionDelegatesToTheArgsTabParser() {
        FinalCMDPluginCommand command = newHarness().register(new E3_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("false", "true"), harness.tab(command, sender, "sub", ""));
        assertEquals(List.of("true"), harness.tab(command, sender, "sub", "tr"));
    }

    // ------------------------------------------------------------------
    // E4 - a position with no @Arg parser returns an empty list
    // ------------------------------------------------------------------

    @Test
    void e4_positionWithoutAParserReturnsEmptyList() {
        FinalCMDPluginCommand command = newHarness().register(new E3_Cmd()); //only 1 @Arg (index 1)
        TestCommandSender sender = new TestCommandSender("console");

        assertTrue(harness.tab(command, sender, "sub", "true", "").isEmpty());
    }

    // ------------------------------------------------------------------
    // E5 - the interpreter's own permission denied -> empty list, even at a value position
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "e5cmd")
    public static class E5_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub", permission = "e5.perm")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Boolean value) {}
    }

    @Test
    void e5_interpreterPermissionDeniedReturnsEmptyListAtValuePosition() {
        FinalCMDPluginCommand command = newHarness().register(new E5_Cmd());
        TestCommandSender sender = new TestCommandSender("console"); //lacks "e5.perm"

        assertTrue(harness.tab(command, sender, "sub", "").isEmpty());
    }
}
