package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A region named on a command line: by full id anywhere, by bare name only where it is unambiguous. */
class ArgParserRegionTest extends RegionCommandFixture {

    static final List<Region> SEEN = new ArrayList<>();

    @FinalCMD(aliases = "probe")
    public static class Probe_Cmd {
        @FinalCMD.SubCMD(subcmd = "get")
        public void get(FCommandSender sender, @Arg("<region>") Region region) {
            SEEN.add(region);
        }

        @FinalCMD.SubCMD(subcmd = "here")
        public void here(FCommandSender sender, @Arg(value = "<region>", fromSender = true) Region region) {
            SEEN.add(region);
        }
    }

    private Region protArena;
    private Region duelArena;

    @BeforeEach
    void regions() {
        SEEN.clear();
        protArena = prot.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
        duelArena = duel.cuboid("arena", WORLD, 0, 0, 0, 20, 20, 20);
    }

    @Test
    void fullIdResolvesAnyOwner() {
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());

        harness.dispatch(probe, Senders.console(), "get theduel:arena");
        harness.dispatch(probe, Senders.console(), "get FakeProt:ARENA");

        assertEquals(Arrays.asList(duelArena, protArena), SEEN);
    }

    @Test
    void bareNameResolvesWhenUnique() {
        Region lobby = duel.cuboid("lobby", WORLD, 100, 0, 100, 110, 10, 110);
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());

        harness.dispatch(probe, Senders.console(), "get lobby");

        assertEquals(Collections.singletonList(lobby), SEEN);
    }

    @Test
    void bareNameSharedByTwoOwnersIsDeniedNamingBoth() {
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());
        TestCommandSender console = Senders.console();

        harness.dispatch(probe, console, "get arena");

        assertTrue(SEEN.isEmpty());
        console.assertAnyMessageContains("fakeprot:arena, theduel:arena");
        console.assertAnyMessageContains("owner:name");
    }

    @Test
    void unknownRegionIsUnrecognized() {
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());
        TestCommandSender console = Senders.console();

        harness.dispatch(probe, console, "get nowhere");
        harness.dispatch(probe, console, "get theduel:nowhere");

        assertTrue(SEEN.isEmpty());
        console.assertAnyMessageContains("nowhere");
    }

    @Test
    void fromSenderFindsTheRegionUnderThePlayer() {
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());
        steve.at(WORLD, 15, 15, 15);   //inside theduel:arena only

        harness.dispatch(probe, steve, "here");

        assertEquals(Collections.singletonList(duelArena), SEEN);
    }

    @Test
    void fromSenderWithTwoRegionsIsDenied() {
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());

        harness.dispatch(probe, steve, "here");   //5 5 5 is in both arenas

        assertTrue(SEEN.isEmpty());
        steve.assertAnyMessageContains("fakeprot:arena, theduel:arena");
    }

    @Test
    void fromSenderOnConsoleIsRefused() {
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());
        TestCommandSender console = Senders.console();

        harness.dispatch(probe, console, "here");

        assertTrue(SEEN.isEmpty());
        console.assertAnyMessageContains("Only a player stands somewhere");
    }

    @Test
    void tabListsIdsByPrefix() {
        prot.cuboid("lobby", WORLD, 100, 0, 100, 110, 10, 110);
        FinalCMDPluginCommand probe = harness.register(new Probe_Cmd());

        assertEquals(Arrays.asList("theduel:arena"), harness.tab(probe, Senders.console(), "get", "the"));
        assertEquals(Arrays.asList("fakeprot:arena", "fakeprot:lobby"), harness.tab(probe, Senders.console(), "get", "fake"));
    }

    @Test
    void insideANodeABareNameMeansTheNodesOwner() {
        FinalCMDPluginCommand fp = harness.register(new Fp_Cmd(protNode()));
        duel.cuboid("duelonly", WORLD, 200, 0, 200, 210, 10, 210);
        steve.grant("fp.region");

        harness.dispatch(fp, steve, "region info arena");
        steve.assertAnyMessageContains("fakeprot:arena");
        assertFalse(steve.anyMessageContains("theduel:arena"), steve.getMessages().toString());

        assertEquals(Collections.singletonList("arena"), harness.tab(fp, steve, "region", "info", ""));

        steve.clearMessages();
        harness.dispatch(fp, steve, "region info duelonly");   //another owner's bare name is not this node's
        steve.assertAnyMessageContains("There is no region called");

        steve.clearMessages();
        harness.dispatch(fp, steve, "region here");   //standing in both arenas, only the node's own counts
        steve.assertAnyMessageContains("fakeprot:arena");
        assertFalse(steve.anyMessageContains("theduel:arena"), steve.getMessages().toString());
    }

    @Test
    void insideTheGroupBranchABareNameStillMeansTheNodesOwner() {
        FinalCMDPluginCommand fp = harness.register(new Fp_Cmd(protNode()));
        steve.grant("fp.region");
        UUID member = UUID.randomUUID();

        harness.dispatch(fp, steve, "region group arena membros add " + member);

        assertNotNull(protArena.getGroupIfPresent("membros"), steve.getMessages().toString());
        assertNotNull(protArena.getGroupIfPresent("membros").getMembership(member));
        assertEquals(null, duelArena.getGroupIfPresent("membros"));
    }

    @Test
    void insideANodeAForeignFullIdStillParses() {
        FinalCMDPluginCommand fp = harness.register(new Fp_Cmd(protNode()));
        steve.grant("fp.region");

        harness.dispatch(fp, steve, "region flag theduel:arena deny-pvp true");

        //the parser handed the foreign region over; the verb is what refused to write to it
        steve.assertAnyMessageContains("belongs to");
        steve.assertAnyMessageContains("theduel");
        assertEquals(null, valueOf(duelArena, "deny-pvp"));
        assertSame(duelArena, prot.getLookup().byId(duelArena.getId()));
    }
}
