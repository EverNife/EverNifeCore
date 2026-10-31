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

/** The group verbs a plugin mounts under its own command. */
class GroupCommandNodeTest extends GroupCommandFixture {

    /** {@code /fparty party ...}, as a party plugin mounts the node. */
    @FinalCMD(aliases = "fparty")
    public static class Party_Cmd {
        @FinalCMD.Node(subcmd = "party", permission = "fp.party")
        final GroupCommandNode node;

        Party_Cmd(GroupCommandNode node) {
            this.node = node;
        }
    }

    private FinalCMDPluginCommand mount(String permissionBase) {
        return harness.register(new Party_Cmd(new GroupCommandNode(parties.getManager(), party, permissionBase)));
    }

    private FinalCMDPluginCommand mount() {
        return mount(null);
    }

    @Test
    void treeHasEveryVerb() {
        harness.tree(mount()).at("party").hasChildren("create", "demote", "disband", "flag", "info", "invite", "join", "kick", "leave", "list",
                "promote");
    }

    @Test
    void createMakesTheGroupWithItsLeaderAsAMember() {
        harness.dispatch(mount(), steve, "party create alpha Steve");

        Group alpha = parties.getManager().get("alpha");
        assertNotNull(alpha, steve.getMessages().toString());
        assertEquals(steve.getUniqueId(), alpha.getLeader());
        assertTrue(alpha.isMember(steve.getUniqueId()));
    }

    @Test
    void createOfANameTakenSaysWhy() {
        parties.group("alpha", party);

        harness.dispatch(mount(), steve, "party create alpha");

        steve.assertAnyMessageContains("alpha");
        assertEquals(1, parties.getManager().getAll().size());
    }

    @Test
    void inviteThenJoinGoesThroughTheInvite() {
        FinalCMDPluginCommand fp = mount();
        Group alpha = parties.group("alpha", party);

        harness.dispatch(fp, steve, "party invite alpha Alex");
        assertNotNull(alpha.getInvite(alex.getUniqueId()));
        harness.dispatch(fp, alex, "party join alpha");

        assertTrue(alpha.isMember(alex.getUniqueId()), alex.getMessages().toString());
        assertNull(alpha.getInvite(alex.getUniqueId()), "the invite was used");
    }

    @Test
    void joinOfAFullGroupTellsThePlayerWhy() {
        Group alpha = parties.group("alpha", party);
        alpha.add(steve.getUniqueId());
        alpha.setFlag(CoreFlags.MAX_MEMBERS, 1);

        harness.dispatch(mount(), alex, "party join alpha");

        assertFalse(alpha.isMember(alex.getUniqueId()));
        alex.assertAnyMessageContains("is full (1/1, max-members)");
    }

    @Test
    void leaveWithoutAGroupLeavesTheSendersInAOneKind() {
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());

        harness.dispatch(mount(), alex, "party leave");

        assertFalse(alpha.isMember(alex.getUniqueId()), alex.getMessages().toString());
    }

    @Test
    void kickTakesTheMemberOut() {
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());

        harness.dispatch(mount(), steve, "party kick alpha Alex");

        assertFalse(alpha.isMember(alex.getUniqueId()));
    }

    @Test
    void promoteAndDemoteMoveAMemberAlongTheLadderAndAnUnknownRoleNamesIt() {
        FinalCMDPluginCommand fp = mount();
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());

        harness.dispatch(fp, steve, "party promote alpha Alex officer");
        assertTrue(alpha.getMember(alex.getUniqueId()).hasRole("officer"));
        harness.dispatch(fp, steve, "party demote alpha Alex officer");
        assertFalse(alpha.getMember(alex.getUniqueId()).hasRole("officer"));
        harness.dispatch(fp, steve, "party promote alpha Alex captain");
        steve.assertAnyMessageContains("[officer]");
    }

    @Test
    void disbandDissolvesTheGroup() {
        Group alpha = parties.group("alpha", party);

        harness.dispatch(mount(), steve, "party disband alpha");

        assertTrue(alpha.isDissolved());
        assertNull(parties.getManager().get("alpha"));
    }

    @Test
    void infoAndListShowTheGroups() {
        FinalCMDPluginCommand fp = mount();
        Group alpha = parties.group("alpha", party);
        alpha.add(alex.getUniqueId());
        alpha.addRole(alex.getUniqueId(), party.getRole("officer"));

        harness.dispatch(fp, steve, "party info alpha");
        steve.assertAnyMessageContains("member §fAlex §7[officer]");
        harness.dispatch(fp, steve, "party list");
        steve.assertAnyMessageContains("§ealpha §71 member(s)");
    }

    @Test
    void flagSetsReadsAndClearsAValueOfTheGroup() {
        FinalCMDPluginCommand fp = mount();
        Group alpha = parties.group("alpha", party);

        harness.dispatch(fp, steve, "party flag alpha max-members 4");
        assertEquals(Integer.valueOf(4), alpha.getFlag(CoreFlags.MAX_MEMBERS), steve.getMessages().toString());
        harness.dispatch(fp, steve, "party flag alpha max-members");
        steve.assertAnyMessageContains("evernifecore:max-members = 4");
        harness.dispatch(fp, steve, "party flag alpha max-members -");
        assertNull(alpha.getFlag(CoreFlags.MAX_MEMBERS));
    }

    @Test
    void aPermissionBaseGatesEachVerb() {
        FinalCMDPluginCommand fp = mount("fp.party");
        steve.grant("fp.party.list");

        harness.dispatch(fp, steve, "party create alpha");
        assertNull(parties.getManager().get("alpha"), "no fp.party.create");
        harness.dispatch(fp, steve, "party list");
        steve.assertAnyMessageContains("Every party");
    }

}
