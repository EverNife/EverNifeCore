package br.com.finalcraft.evernifecore.region.command;

import java.util.Collections;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.flag.FlagMode;
import br.com.finalcraft.evernifecore.flag.AmountEvery;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.EntryDenySettings;
import br.com.finalcraft.evernifecore.flag.core.MoveDenySettings;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /ecore region here|info|list}: reads every owner's regions and never changes one. */
class CoreRegionCommandsTest extends RegionCommandFixture {

    private FinalCMDPluginCommand ecore;
    private Region arena;
    private Region duelArena;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void regions() {
        ecore = harness.register(new CoreCommand());
        arena = prot.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
        duelArena = duel.cuboid("arena", WORLD, 0, 0, 0, 20, 20, 20);
        duelArena.setFlag((Flag<Tristate>) flag("deny-pvp"), Tristate.TRUE);
        steve.grant("evernifecore.command.region");
    }

    @Test
    void hereListsEveryOwnerAndExplainsEachFlag() {
        harness.dispatch(ecore, steve, "region here");

        steve.assertAnyMessageContains("fakeprot:arena §7prio 0");
        steve.assertAnyMessageContains("theduel:arena §7prio 0");
        String denyPvp = flag("deny-pvp").getKey().toString();
        assertTrue(steve.getMessages().stream().anyMatch(line -> line.contains(denyPvp + ": §etrue") && line.contains("<- theduel:arena")),
                steve.getMessages().toString());
        String hover = steve.hoverTextOfMessageContaining(denyPvp + ":");
        assertNotNull(hover);
        assertTrue(hover.contains("theduel:arena") && hover.contains("LOCAL_VALUE"), hover);
    }

    @Test
    void hereListsEveryLayerWithWhatTheResolverDidSoACutLayerReadsCutBySet() {
        Flag<List<AmountEvery>> heal = (Flag<List<AmountEvery>>) (Flag<?>) flag("heal");
        duelArena.setFlag(heal, FlagValue.permanent(Collections.singletonList(AmountEvery.of(5, Duration.ofSeconds(1)))).withMode(FlagMode.SET)
                .withPriority(1));
        arena.setFlag(heal, Collections.singletonList(AmountEvery.of(2, Duration.ofSeconds(1))));

        harness.dispatch(ecore, steve, "region here");

        String colored = steve.hoverTextOfMessageContaining(heal.getKey() + ":");
        assertNotNull(colored, steve.getMessages().toString());
        String hover = colored.replaceAll("§.", "");
        assertTrue(hover.contains("[COUNTED] theduel:arena") && hover.contains("mode=set"), hover);
        assertTrue(hover.contains("[CUT_BY_SET] fakeprot:arena"), hover);
        steve.assertAnyMessageContains("via sum-by-interval");
    }

    @Test
    void hereOnConsoleWithoutATargetIsRefused() {
        TestCommandSender console = Senders.console().grant("evernifecore.command.region");

        harness.dispatch(ecore, console, "region here");

        console.assertAnyMessageContains("/ecore region here <player>");
        assertFalse(console.anyMessageContains("fakeprot:arena"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void infoPrintsFlagsGroupsMembersAndRemaining() {
        UUID alex = UUID.randomUUID();
        arena.setFlag((Flag<Tristate>) flag("deny-pvp"), Tristate.FALSE, Duration.ofMinutes(10));
        Group members = arena.getGroup("membros");
        members.add(alex, Duration.ofMinutes(15));
        members.setFlag((Flag<Tristate>) flag("deny-block-break"), Tristate.TRUE);
        arena.addTag("spawn");

        harness.dispatch(ecore, steve, "region info fakeprot:arena");

        steve.assertAnyMessageContains("§6Region §efakeprot:arena");
        steve.assertAnyMessageContains("§7owner: §fFakeProt");
        steve.assertAnyMessageContains("10m left");
        steve.assertAnyMessageContains("membros");
        steve.assertAnyMessageContains(alex + " §7(15m left)");
        steve.assertAnyMessageContains("deny-block-break");
        steve.assertAnyMessageContains("spawn");
        steve.assertAnyMessageContains("volume §f1331");
    }

    @Test
    void infoShowsSettingsOverrideAndHandlerOwner() {
        arena.setFlagSettings(flag("deny-entry"), new EntryDenySettings());
        arena.setFlagHandler(prot.getOwner(), flag("deny-pvp"), context -> { });

        harness.dispatch(ecore, steve, "region info fakeprot:arena");
        steve.assertAnyMessageContains("settings: §eoverride");
        steve.assertAnyMessageContains("handler: §eFakeProt");

        steve.clearMessages();
        harness.dispatch(ecore, steve, "region info theduel:arena");
        assertFalse(steve.anyMessageContains("override"), steve.getMessages().toString());
        assertFalse(steve.anyMessageContains("handler:"), steve.getMessages().toString());
    }

    @Test
    void listFiltersByOwnerAndWorld() {
        prot.cuboid("far", "nether", 0, 0, 0, 5, 5, 5);

        harness.dispatch(ecore, steve, "region list fakeprot");
        steve.assertAnyMessageContains("fakeprot:arena");
        steve.assertAnyMessageContains("fakeprot:far");
        assertFalse(steve.anyMessageContains("theduel:arena"), steve.getMessages().toString());

        steve.clearMessages();
        harness.dispatch(ecore, steve, "region list fakeprot nether");
        steve.assertAnyMessageContains("fakeprot:far");
        assertFalse(steve.anyMessageContains("fakeprot:arena"), steve.getMessages().toString());

        steve.clearMessages();
        harness.dispatch(ecore, steve, "region list");
        steve.assertAnyMessageContains("theduel:arena");
        steve.assertAnyMessageContains("fakeprot:arena");
    }

    @Test
    void withoutThePermissionNothingIsPrinted() {
        TestFPlayerSender stranger = Senders.player("Stranger").at(WORLD, 5, 5, 5);

        harness.dispatch(ecore, stranger, "region here");
        harness.dispatch(ecore, stranger, "region info fakeprot:arena");
        harness.dispatch(ecore, stranger, "region list");

        assertFalse(stranger.anyMessageContains("arena"), stranger.getMessages().toString());
        stranger.assertAnyMessageContains("evernifecore.command.region");
    }

    @Test
    void coreVerbsNeverMutate() {
        Map<String, Object> before = snapshot();

        harness.dispatch(ecore, steve, "region here");
        harness.dispatch(ecore, steve, "region info fakeprot:arena");
        harness.dispatch(ecore, steve, "region info theduel:arena");
        harness.dispatch(ecore, steve, "region list");
        harness.dispatch(ecore, steve, "region list theduel world");

        assertEquals(before, snapshot());
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> state = new HashMap<>();
        state.put("count", prot.getLookup().all().size());
        for (Region region : prot.getLookup().all()) {
            List<Object> facts = new ArrayList<>();
            facts.add(new HashMap<>(region.getFlags()));
            facts.add(new ArrayList<>(region.getGroups().keySet()));
            facts.add(new ArrayList<>(region.getTags()));
            facts.add(region.getPriority());
            facts.add(region.getParent());
            facts.add(region.getShape());
            state.put(region.getId().toString(), facts);
        }
        return state;
    }
}
