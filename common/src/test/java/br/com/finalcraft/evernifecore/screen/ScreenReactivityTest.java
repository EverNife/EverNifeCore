package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenReactivityTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    /** A condition that answers after the sources it names, counting how often it is asked. */
    private static Condition after(AtomicInteger asked, EventSource... sources) {
        Set<EventSource> needs = new LinkedHashSet<>();
        Collections.addAll(needs, sources);
        return new Condition() {
            @Override
            public ConditionResult test(ExecutionContext ctx) {
                asked.incrementAndGet();
                return ConditionResult.pass();
            }

            @Override
            public Set<EventSource> getNeeds() {
                return needs;
            }
        };
    }

    private static Icon stone() {
        return Icon.of(ItemSpec.of("minecraft:stone", 1));
    }

    private static void toggleFlight(FPlayer player) {
        SimpleContext context = new SimpleContext(EventSource.FLIGHT_TOGGLE, WorldLocPos.of(0, 64, 0, "world"),
                Actor.player(player, null), null);
        ECEventBus.global().post(ActionEvents.of(EventSource.FLIGHT_TOGGLE).newPost(context));
    }

    @Test
    void openingHoldsEverySourceTheConditionsNeed() {
        Screen<?> screen = Screen.of(1);
        screen.icon(0, stone()).id("loot").dependsOn(after(new AtomicInteger(), EventSource.ITEM_PICKUP, EventSource.BREAK));

        ScreenView view = world.open(screen, alex);

        assertEquals(new LinkedHashSet<>(Arrays.asList(EventSource.ITEM_PICKUP, EventSource.BREAK)), view.getHeldSources());
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.ITEM_PICKUP), "a source somebody looks at is needed");
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void closingReleasesEveryHold() {
        Screen<?> screen = Screen.of(1);
        screen.icon(0, stone()).id("loot").dependsOn(after(new AtomicInteger(), EventSource.ITEM_PICKUP));
        ScreenView view = world.open(screen, alex);

        view.close();
        world.advanceTicks(2);

        assertTrue(view.isClosed());
        assertTrue(view.getHeldSources().isEmpty());
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.ITEM_PICKUP), "nobody looks any more, so nothing is needed");
    }

    @Test
    void twoInvalidationsInOneTickAreOneWriteOnTheSurface() {
        MutableState<Integer> counter = new MutableState<>(1);
        Screen<?> screen = Screen.of(1);
        screen.icon(0, stone()).id("loot").dependsOn(after(new AtomicInteger(), EventSource.FLIGHT_TOGGLE))
                .render(icon -> icon.lore("flying"));
        screen.icon(1, stone()).id("counter").dependsOn(counter).render(icon -> icon.amount(counter.get()));
        ScreenView view = world.open(screen, alex);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        int before = surface.getApplies().size();
        assertTrue(before > 0, "the open drew the screen once already");

        toggleFlight(alex);
        counter.set(7);
        world.advanceTicks(1);

        assertEquals(before + 1, surface.getApplies().size(), "both changes land in one apply");
        assertEquals(7, surface.getSlot(1).getItem().getAmount());
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void anActionOfAnotherPlayerRedrawsNothing() {
        AtomicInteger redraws = new AtomicInteger();
        Screen<?> screen = Screen.of(1);
        screen.icon(0, stone()).id("loot").dependsOn(after(new AtomicInteger(), EventSource.FLIGHT_TOGGLE))
                .render(icon -> redraws.incrementAndGet());
        ScreenView view = world.open(screen, alex);
        int afterOpen = redraws.get();

        toggleFlight(world.viewer("Bruno"));
        world.advanceTicks(1);
        assertEquals(afterOpen, redraws.get(), "Bruno's flight is not Alex's business");

        toggleFlight(alex);
        world.advanceTicks(1);
        assertEquals(afterOpen + 1, redraws.get(), "Alex's own flight redraws Alex's button");
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void aConditionWithNoSourceIsAskedOnlyAtItsSampleInterval() {
        AtomicInteger asked = new AtomicInteger();
        Screen<?> screen = Screen.of(1);
        screen.icon(0, stone()).id("rank").dependsOn(after(asked)).sampleEvery(10);
        ScreenView view = world.open(screen, alex);
        int afterOpen = asked.get();

        world.advanceTicks(9);
        assertEquals(afterOpen, asked.get(), "nine ticks are not a sample of ten");
        world.advanceTicks(1);
        assertEquals(afterOpen + 1, asked.get(), "the tenth tick asks once");
        assertTrue(view.getHeldSources().isEmpty(), "a condition with no source holds nothing");
        view.close();
        world.advanceTicks(2);
    }

}
