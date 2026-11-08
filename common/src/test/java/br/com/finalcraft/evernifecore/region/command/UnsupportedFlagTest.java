package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flag the platform cannot serve: a double that raises every source but the hopper's leaves {@code owner-only-pickup}
 * registered and inert - silent, out of what the verbs list, and asked by nothing even though its drop and pickup are raised.
 */
class UnsupportedFlagTest extends RegionCommandFixture {

    private static final WorldLocPos AT = WorldLocPos.of(5, 5, 5, WORLD);

    private Region yard;

    /** The platform double: it raises every core source but {@link EventSource#ITEM_HOPPER_PICKUP}. */
    private static void declareNoHopperSource() {
        List<EventSource> raised = new ArrayList<>(EventSource.all());
        raised.remove(EventSource.ITEM_HOPPER_PICKUP);
        ActionSupport.declare("A platform without hoppers", raised, null, false, null);
    }

    @BeforeEach
    void yard() {
        yard = prot.cuboid("yard", WORLD, 0, 0, 0, 10, 10, 10);
        steve.grant("evernifecore.command.region");
        steve.grant("fp.region");
    }

    @AfterEach
    void undeclare() {
        ActionSupport.declare("an undeclared platform", null, null, true, null);
    }

    @Test
    void itIsSilentOnTheBootAndOnARegistrationAfterIt() {
        FlagRegistry.get().unregister(CoreFlags.OWNER_ONLY_PICKUP);
        List<String> logged = Logs.capture(() -> {
            declareNoHopperSource();
            FlagRegistry.get().register(core, CoreFlags.OWNER_ONLY_PICKUP);
        });

        assertFalse(FlagRegistry.get().isSupported(CoreFlags.OWNER_ONLY_PICKUP));
        assertNull(FlagRegistry.get().describePartialEffect(CoreFlags.OWNER_ONLY_PICKUP));
        assertTrue(logged.stream().noneMatch(line -> line.contains("owner-only-pickup")), logged.toString());
    }

    @Test
    void aFlagMissingOneSourceItDoesNotRequireStillActsInPartAndSaysSo() {
        TristateFlag tracks = new TristateFlag(FlagKey.of(core, "tracks-hoppers"),
                new LinkedHashSet<>(Arrays.asList(EventSource.ITEM_DROP, EventSource.ITEM_HOPPER_PICKUP)), Tristate.FALSE, Tristate.TRUE);
        FlagRegistry.get().register(core, tracks);

        List<String> logged = Logs.capture(UnsupportedFlagTest::declareNoHopperSource);

        assertTrue(FlagRegistry.get().isSupported(tracks));
        assertTrue(logged.stream().anyMatch(line -> line.contains("tracks-hoppers acts in part")), logged.toString());
    }

    @Test
    void itLeavesTheTabCompletionAndTheVerbsRefuseItsName() {
        FinalCMDPluginCommand fp = harness.register(new Fp_Cmd(protNode()));
        assertTrue(harness.tab(fp, steve, "region", "flag", "yard", "owner-only").contains("owner-only-pickup"), "a platform that serves it lists it");

        declareNoHopperSource();

        assertTrue(harness.tab(fp, steve, "region", "flag", "yard", "owner-only").isEmpty());
        assertTrue(harness.tab(fp, steve, "region", "flag", "yard", "deny-pv").contains("deny-pvp"), "the others stay");
        harness.dispatch(fp, steve, "region flag yard owner-only-pickup true");
        assertNull(yard.getFlag(CoreFlags.OWNER_ONLY_PICKUP), "for the verb there is no such flag");
    }

    @Test
    void theListingsOfTheCoreVerbsLeaveItOut() {
        yard.setFlag(CoreFlags.OWNER_ONLY_PICKUP, Tristate.TRUE);
        yard.setFlag(CoreFlags.DENY_PVP, Tristate.TRUE);
        FinalCMDPluginCommand ecore = harness.register(new CoreCommand());
        declareNoHopperSource();

        harness.dispatch(ecore, steve, "region info " + yard.getId());
        harness.dispatch(ecore, steve, "region here");

        steve.assertAnyMessageContains("deny-pvp");
        assertTrue(steve.getMessages().stream().noneMatch(line -> line.contains("owner-only-pickup")), steve.getMessages().toString());
    }

    @Test
    void aValueSetByApiIsKeptAndNothingAsksItNorListensForIt() {
        declareNoHopperSource();
        EventSourceUsage usage = EventSourceUsage.get();

        List<String> logged = Logs.capture(() -> yard.setFlag(CoreFlags.OWNER_ONLY_PICKUP, Tristate.TRUE));

        assertEquals(Tristate.TRUE, yard.getFlag(CoreFlags.OWNER_ONLY_PICKUP), "the value is kept for a platform that serves it");
        assertTrue(logged.stream().noneMatch(line -> line.contains("owner-only-pickup")), logged.toString());
        assertFalse(usage.isNeeded(EventSource.ITEM_PICKUP), "its value puts no listener on the server");

        TestFPlayerSender thief = new TestFPlayerSender("thief");
        ItemContext drop = new ItemContext(EventSource.ITEM_DROP, AT, Actors.player(thief), null, TypeKey.parse("minecraft:dirt"), 1, null, null);
        ItemContext pickup = new ItemContext(EventSource.ITEM_PICKUP, AT, Actors.player(thief), null, TypeKey.parse("minecraft:dirt"), 1, null, null);
        pickup.setItemOwner(new TestFPlayerSender("owner").getUniqueId());

        assertEquals(Decision.PASS, prot.getLookup().decide(drop));
        assertNull(drop.getItemOwner(), "the flag was not asked about the drop");
        assertEquals(Decision.PASS, prot.getLookup().decide(pickup), "nor about the pickup");
    }
}
