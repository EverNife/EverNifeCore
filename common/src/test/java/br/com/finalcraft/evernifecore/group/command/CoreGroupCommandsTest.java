package br.com.finalcraft.evernifecore.group.command;

import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /ecore group <player>}: every group of a player, read only. */
class CoreGroupCommandsTest extends GroupCommandFixture {

    @Test
    void coreGroupCommandListsEveryGroupOfThePlayerWithItsFlagsLeavingOutTheUnsupported() {
        FinalCMDPluginCommand ecore = harness.register(new CoreCommand());
        steve.grant("evernifecore.command.group");
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());
        alpha.setLeader(alex.getUniqueId());
        alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
        alpha.setFlag(CoreFlags.MAX_MEMBERS, 5);

        harness.dispatch(ecore, steve, "group Alex");

        steve.assertAnyMessageContains("Groups of §eAlex§6: §e1");
        steve.assertAnyMessageContains("fakeparty:alpha §7kind §ffakeparty:party §7leader §fAlex");
        steve.assertAnyMessageContains("max-members");
        assertFalse(steve.anyMessageContains("glow-members"), "this platform cannot glow, so the flag does not exist for the verb");
    }

    @Test
    void eachLineOfAGroupReadsItsEditedText() {
        FinalCMDPluginCommand ecore = harness.register(new CoreCommand());
        steve.grant("evernifecore.command.group");
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());
        alpha.setLeader(alex.getUniqueId());
        alpha.setFlag(CoreFlags.MAX_MEMBERS, 5);

        assertSteveReads("CoreGroupCommands.GROUP_LINE", "§b${group} of ${kind}, ${online}/${size}", "§bfakeparty:alpha of fakeparty:party, 1/1");
        assertSteveReads("CoreGroupCommands.MEMBER_LINE_NEVER_EXPIRES", "§broles ${roles}", "§broles -");
        assertSteveReads("CoreGroupCommands.FLAG_LINE", "§b${flag} is ${value}", "max-members is 5");
    }

    private void assertSteveReads(String key, String text, String expected) {
        steve.clearMessages();
        try (Locales.Retext edited = Locales.retext(harness.ecPluginData, key, harness.ecPluginData.getPluginLanguage(), text)) {
            harness.dispatch(harness.register(new CoreCommand()), steve, "group Alex");
        }
        steve.assertAnyMessageContains(expected);
    }

    @Test
    void aPlayerInNoGroupReadsZero() {
        FinalCMDPluginCommand ecore = harness.register(new CoreCommand());
        steve.grant("evernifecore.command.group");

        harness.dispatch(ecore, steve, "group Steve");

        steve.assertAnyMessageContains("Groups of §eSteve§6: §e0");
    }
}
