package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Costs;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.GatePaidEvent;
import br.com.finalcraft.evernifecore.logic.core.CoreLogic;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.MoveVerdict;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code entry-gate} checks its conditions on every entry and charges its costs as its settings say, through the gate's own pay. */
@ECoreTest
class EntryGateFlagTest {

    private static final String WORLD = "world";
    private static final WorldLocPos OUTSIDE = WorldLocPos.of(50, 5, 5, WORLD);
    private static final WorldLocPos INSIDE = WorldLocPos.of(5.5, 5, 5, WORLD);
    private static final String COIN_GATE = "{require: [{permission: vip.mine}], cost: [{type: 'arenas:coins', amount: 10}]}";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;
    private RecordingAudience audience;
    private TestFPlayerSender alice;
    private final AtomicInteger wallet = new AtomicInteger(100);

    /** Coins out of {@link #wallet}; {@code jammed} passes its test and never takes - a payment that fails half way. */
    private Cost coins(int amount, boolean jammed) {
        return new Cost() {
            @Override
            public ConditionResult test(ExecutionContext ctx) {
                return ConditionResult.of(jammed || wallet.get() >= amount, FancyText.of("Needs " + amount + " coins."));
            }

            @Override
            public boolean take(ExecutionContext ctx) {
                if (jammed || wallet.get() < amount) {
                    return false;
                }
                wallet.addAndGet(-amount);
                return true;
            }

            @Override
            public void refund(ExecutionContext ctx) {
                wallet.addAndGet(amount);
            }

            @Override
            public FancyText describe(ExecutionContext ctx) {
                return FancyText.of(amount + " coins");
            }
        };
    }

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 50, 5, 5);
        world = Platforms.lenient().onlinePlayers(Collections.<FPlayer>singletonList(alice)).install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreLogic.registerAll(core);
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Arenas", tempDir.resolve("arenas"));
        for (boolean jammed : new boolean[]{false, true}) {
            Costs.register(regions.getOwner(), TypeDescriptor.<Cost>of(jammed ? "arenas:jammed" : "arenas:coins")
                    .describe("Coins of the test's wallet.")
                    .param(ParamSpec.required("amount", ParamSpec.Kind.NUMBER, "How many."))
                    .factory(params -> coins(params.getNumber("amount").intValue(), jammed))
                    .build());
        }
        audience = new RecordingAudience("entry-gate");
        ECEventBus.global().addNativeAudience(audience);
        RegionMovementTracker.get().onJoin(alice, OUTSIDE);
    }

    @AfterEach
    void close() {
        ECEventBus.global().removeNativeAudience(audience.getName());
        RegionMovementTracker.get().onQuit(alice);
        FlagRegistry.get().unregisterAll(regions.getOwner());
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    private Region gated(String gate, EntryGateSettings.Charge charge) throws Exception {
        Region mine = regions.cuboid("mine", WORLD, 0, 0, 0, 10, 10, 10);
        mine.setFlag(CoreFlags.ENTRY_GATE, CoreFlags.ENTRY_GATE.getType().parse(gate));
        EntryGateSettings settings = new EntryGateSettings();
        settings.setCharge(charge);
        mine.setFlagSettings(CoreFlags.ENTRY_GATE, settings);
        return mine;
    }

    private MoveVerdict enter() {
        return RegionMovementTracker.get().onMove(alice, OUTSIDE, INSIDE, MoveCause.MOVE, null);
    }

    private void leave() {
        assertTrue(RegionMovementTracker.get().onMove(alice, INSIDE, OUTSIDE, MoveCause.MOVE, null).isAllowed());
    }

    @Test
    void aFailedConditionSendsThePlayerBackAndTellsThemWhy() throws Exception {
        gated(COIN_GATE, EntryGateSettings.Charge.ONCE_PER_SESSION);

        assertFalse(enter().isAllowed(), "no vip.mine: sent back");
        assertTrue(alice.getMessages().stream().anyMatch(line -> line.contains("vip.mine")), String.valueOf(alice.getMessages()));
        assertEquals(100, wallet.get(), "nothing charged");
        assertTrue(audience.getDispatchedOf(GatePaidEvent.class).isEmpty());
    }

    @Test
    void oncePerSessionChargesOnceInThreeEntries() throws Exception {
        gated(COIN_GATE, EntryGateSettings.Charge.ONCE_PER_SESSION);
        alice.grant("vip.mine");

        for (int i = 0; i < 3; i++) {
            assertTrue(enter().isAllowed(), "entry " + i);
            leave();
        }
        assertEquals(90, wallet.get());
        assertEquals(1, audience.getDispatchedOf(GatePaidEvent.class).size());
    }

    @Test
    void everyEntryChargesEveryCrossingIn() throws Exception {
        gated(COIN_GATE, EntryGateSettings.Charge.EVERY_ENTRY);
        alice.grant("vip.mine");

        for (int i = 0; i < 3; i++) {
            assertTrue(enter().isAllowed(), "entry " + i);
            leave();
        }
        assertEquals(70, wallet.get());
        assertEquals(3, audience.getDispatchedOf(GatePaidEvent.class).size());
    }

    @Test
    void aCostThatCannotBeTakenSendsThePlayerBackAndChargesNothing() throws Exception {
        gated("{cost: [{type: 'arenas:coins', amount: 10}, {type: 'arenas:jammed', amount: 1}]}", EntryGateSettings.Charge.EVERY_ENTRY);

        assertFalse(enter().isAllowed(), "the second cost did not come out: sent back");
        assertEquals(100, wallet.get(), "the coins taken first were given back");
        assertTrue(audience.getDispatchedOf(GatePaidEvent.class).isEmpty());
        assertTrue(alice.getMessages().stream().anyMatch(line -> line.contains("could not be taken")), String.valueOf(alice.getMessages()));
    }

    @Test
    void aBypassingPlayerEntersWithoutPaying() throws Exception {
        gated(COIN_GATE, EntryGateSettings.Charge.EVERY_ENTRY);
        alice.grant("evernifecore.region.bypass.*");

        assertTrue(enter().isAllowed());
        assertEquals(100, wallet.get());
        assertTrue(audience.getDispatchedOf(GatePaidEvent.class).isEmpty());
    }

    @Test
    void oncePaidTheConditionsStillHoldOnEveryEntry() throws Exception {
        gated(COIN_GATE, EntryGateSettings.Charge.ONCE_PER_SESSION);
        alice.grant("vip.mine");
        assertTrue(enter().isAllowed());
        leave();

        wallet.set(0);
        assertTrue(enter().isAllowed(), "paid this session: an empty wallet does not matter");
        leave();
        alice.revoke("vip.mine");
        assertFalse(enter().isAllowed(), "the condition is checked again on every entry");
    }
}
