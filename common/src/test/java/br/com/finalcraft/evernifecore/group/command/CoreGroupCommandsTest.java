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
    void coreGroupCommandListsEveryGroupOfThePlayerWithItsFlagsAndTheGlowSupport() {
        FinalCMDPluginCommand ecore = harness.register(new CoreCommand());
        steve.grant("evernifecore.command.group");
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());
        alpha.setLeader(alex.getUniqueId());
        alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);

        harness.dispatch(ecore, steve, "group Alex");

        steve.assertAnyMessageContains("Groups of §eAlex§6: §e1");
        steve.assertAnyMessageContains("fakeparty:alpha §7kind §ffakeparty:party §7leader §fAlex");
        steve.assertAnyMessageContains("glow-members: unsupported on this platform");
    }

    @Test
    void aPlayerInNoGroupReadsZero() {
        FinalCMDPluginCommand ecore = harness.register(new CoreCommand());
        steve.grant("evernifecore.command.group");

        harness.dispatch(ecore, steve, "group Steve");

        steve.assertAnyMessageContains("Groups of §eSteve§6: §e0");
    }
}
