package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcome;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcomeContext;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Revalidation;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.flag.core.FarewellFlag;
import br.com.finalcraft.evernifecore.region.flag.core.GreetingFlag;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Session flags follow the player: entered, revalidated as their policy says, left - and their handler hears it. */
class SessionFlagsTest extends TrackerFixture {

    /** A session text flag that records every call, under the revalidation it is given. */
    private static final class TracingFlag extends RegionFlag<String> {
        final List<String> calls = new CopyOnWriteArrayList<>();
        private final Revalidation revalidation;

        TracingFlag(ECPluginData owner, String name, Revalidation revalidation) {
            super(FlagKey.of(owner, name), FlagTypes.TEXT, Collections.singleton(EventSource.MOVE), null);
            this.revalidation = revalidation;
        }

        @Override
        public FlagCategory getCategory() {
            return FlagCategory.SESSION;
        }

        @Override
        public Revalidation getRevalidation() {
            return revalidation;
        }

        @Override
        public String resolveTie(List<String> values) {
            return values.get(0);
        }

        @Override
        public void onEnter(SessionContext context, String value) {
            calls.add("enter " + value);
        }

        @Override
        public void onLeave(SessionContext context, String lastValue) {
            calls.add("leave " + lastValue);
        }

        @Override
        public void onRevalidate(SessionContext context, String oldValue, String newValue) {
            calls.add("revalidate " + oldValue + "->" + newValue);
        }
    }

    private TracingFlag tracing(String name, Revalidation revalidation) {
        return registered(new TracingFlag(owner, name, revalidation));
    }

    @Test
    void greetingAndFarewellSpeakOnceAndAcrossAStraightCrossing() {
        GreetingFlag greeting = registered(new GreetingFlag());
        FarewellFlag farewell = registered(new FarewellFlag());
        Region a = region("a", 0, 10);
        Region b = region("b", 11, 20);
        a.setFlag(greeting, "welcome to A");
        a.setFlag(farewell, "bye from A");
        b.setFlag(greeting, "welcome to B");
        b.setFlag(farewell, "bye from B");

        tracker.onJoin(player, at(5.5));
        tracker.onMove(player, at(5.5), at(6.5), MoveCause.MOVE, null);
        tracker.onMove(player, at(10.5), at(11.5), MoveCause.MOVE, null);
        tracker.onMove(player, at(11.5), at(30.5), MoveCause.MOVE, null);
        tracker.onJoin(player, at(15.5));
        tracker.onQuit(player);

        List<String> said = player.getMessages();
        assertEquals(6, said.size(), said.toString());
        assertEquals("welcome to A", said.get(0));
        assertEquals(new HashSet<>(Arrays.asList("welcome to B", "bye from A")), new HashSet<>(said.subList(1, 3)),
                "crossing straight from A to B greets with B and says goodbye with A");
        assertEquals(Arrays.asList("bye from B", "welcome to B", "bye from B"), said.subList(3, 6));
    }

    @Test
    void everyLookupRevalidatesOnEveryBlockAndOnBoundaryDoesNot() {
        TracingFlag everyBlock = tracing("every-block", Revalidation.everyLookup());
        TracingFlag onBoundary = tracing("on-boundary", Revalidation.onBoundary());
        Region room = region("room", 0, 10);
        room.setFlag(everyBlock, "x");
        room.setFlag(onBoundary, "y");

        tracker.onJoin(player, at(2.5));
        tracker.onMove(player, at(2.5), at(3.5), MoveCause.MOVE, null);
        tracker.onMove(player, at(3.5), at(4.5), MoveCause.MOVE, null);

        assertEquals(Arrays.asList("enter x", "revalidate x->x", "revalidate x->x"), everyBlock.calls);
        assertEquals(Collections.singletonList("enter y"), onBoundary.calls);
    }

    @Test
    void anIntervalFlagIsCheckedByTheTickOnItsWorldsThreadAndTheTickStopsWithIt(TestPlatform platform) {
        TracingFlag timed = tracing("timed", Revalidation.interval(Duration.ofMinutes(1)));
        Region room = region("room", 0, 10);
        room.setFlag(timed, "t");
        tracker.onJoin(player, at(5.5));
        SessionFlags sessionFlags = tracker.getSessionFlags();
        assertTrue(sessionFlags.isTicking(), "the first interval flag applied starts the tick");
        platform.reset();

        clock.advance(Duration.ofSeconds(30));
        agenda.runQueued();
        assertEquals(Collections.singletonList("enter t"), timed.calls, "a tick before the check is due does nothing");

        clock.advance(Duration.ofSeconds(31));
        agenda.runQueued();
        assertEquals(Arrays.asList("enter t", "revalidate t->t"), timed.calls);
        assertEquals(Collections.singletonList(WORLD), platform.getWorldThreadRuns());
        assertEquals(Instant.parse("2026-10-06T12:02:01Z"), session().getNextRevalidation(timed));

        tracker.onMove(player, at(5.5), at(30.5), MoveCause.MOVE, null);
        agenda.runQueued();
        agenda.runQueued();
        assertFalse(sessionFlags.isTicking(), "the last interval flag left: the tick stops on its own");
        assertEquals(0, agenda.size());
    }

    @Test
    void theRevalidationIntervalOfTheSettingsOverridesTheFlagsAndTheRegionsOverridesTheServers() {
        TracingFlag timed = tracing("timed", Revalidation.interval(Duration.ofMinutes(10)));
        Region room = region("room", 0, 10);
        room.setFlag(timed, "t");
        timed.getSettings().setRevalidationInterval(Duration.ofMinutes(2));

        tracker.onJoin(player, at(5.5));
        assertEquals(clock.instant().plus(Duration.ofMinutes(2)), session().getNextRevalidation(timed), "the server's settings");

        FlagSettings own = new FlagSettings();
        own.setRevalidationInterval(Duration.ofSeconds(20));
        room.setFlagSettings(timed, own);
        bus.post(new RegionChangedEvent(room, RegionChangedEvent.Type.FLAG_SETTINGS));
        agenda.runQueued();

        assertEquals(clock.instant().plus(Duration.ofSeconds(20)), session().getNextRevalidation(timed),
                "a settings change of the region is looked at again, with the region's interval");
    }

    @Test
    void aCustomPolicyChecksWhenItsFunctionSays() {
        Instant when = Instant.parse("2026-10-06T12:00:42Z");
        TracingFlag custom = tracing("custom", Revalidation.custom(context -> when));
        region("room", 0, 10).setFlag(custom, "c");

        tracker.onJoin(player, at(5.5));

        assertEquals(when, session().getNextRevalidation(custom));
    }

    @Test
    void invalidatingThePlayerOrChangingAFlagOfTheirRegionChecksAgainOnTheNextTick() {
        TracingFlag plain = tracing("plain", Revalidation.onBoundary());
        Region room = region("room", 0, 10);
        room.setFlag(plain, "one");
        tracker.onJoin(player, at(5.5));

        lookup.invalidate(player.getUniqueId());
        agenda.runQueued();
        assertEquals(Arrays.asList("enter one", "revalidate one->one"), plain.calls);

        room.setFlag(plain, "two");
        bus.post(new RegionFlagChangedEvent(room, null, plain, null, null, FlagChangeCause.SET));
        agenda.runQueued();
        assertEquals(Arrays.asList("enter one", "revalidate one->one", "revalidate one->two"), plain.calls);
    }

    @Test
    void theHandlerHearsAppliedAndRemovedWithNoAction() {
        TracingFlag plain = tracing("plain", Revalidation.onBoundary());
        Region room = region("room", 0, 10);
        room.setFlag(plain, "v");
        List<FlagOutcomeContext> heard = new ArrayList<>();
        room.setFlagHandler(other, plain, heard::add);

        tracker.onJoin(player, at(5.5));
        tracker.onMove(player, at(5.5), at(30.5), MoveCause.MOVE, null);

        assertEquals(2, heard.size());
        assertEquals(FlagOutcome.APPLIED, heard.get(0).getOutcome());
        assertEquals(FlagOutcome.REMOVED, heard.get(1).getOutcome());
        assertNull(heard.get(0).getAction());
        assertEquals("v", heard.get(1).getValue());
    }
}
