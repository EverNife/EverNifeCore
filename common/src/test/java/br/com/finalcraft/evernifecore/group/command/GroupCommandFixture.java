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

/** A party plugin with a ONE kind (ladder: officer), two players online, the core flags and the group messages loaded. */
abstract class GroupCommandFixture {

    @TempDirNobodyCleans
    Path tempDir;

    FinalCmdTestHarness harness;
    ECPluginData core;
    GroupWorld parties;
    GroupKind party;
    TestFPlayerSender steve;
    TestFPlayerSender alex;

    @BeforeEach
    void open() {
        harness = Commands.harness("GroupCmd", tempDir.resolve("cmd"));
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.ONE, GroupRole.of("officer", 10));
        FCLocaleManager.loadLocale(harness.ecPluginData, GroupCommandNode.class, CoreGroupCommands.class);
        steve = parties.player("Steve");
        alex = parties.player("Alex");
        steve.grant("fp.party");
        alex.grant("fp.party");
    }

    @AfterEach
    void close() {
        parties.close();
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        harness.close();
    }

}
