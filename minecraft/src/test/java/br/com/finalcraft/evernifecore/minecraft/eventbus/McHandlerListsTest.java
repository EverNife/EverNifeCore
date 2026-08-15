package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which HandlerList an EC event answers to. Registration, dispatch and the audience's gate all ask
 * this one question, so the interesting assertion is always that the three got the same answer -
 * whether the event declares a list of its own, inherits one or has none at all.
 */
class McHandlerListsTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void anEventDeclaringItsOwnListIsRegisteredAndDispatchedThroughIt() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            HandlerList own = (HandlerList) ECEvent.getHandlerListOf(DeclaredListEvent.class);
            assertEquals(0, own.getRegisteredListeners().length, "nobody has registered into it yet");

            world.listen(DeclaredListEvent.class, event -> {
            });

            assertEquals(1, own.getRegisteredListeners().length, "the plugin manager put the listener in the declared list");
            assertSame(own, new DeclaredListEvent().getHandlers(), "and a dispatch reads that same list");
            assertNotSame(own, ECEvent.getHandlerList(), "a declared list is never the family fallback");
        }
    }

    @Test
    void anEventDeclaringNoListIsRegisteredAndDispatchedThroughTheFamilyOne() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            HandlerList family = ECEvent.getHandlerList();
            int before = family.getRegisteredListeners().length;

            world.listen(UndeclaredListEvent.class, event -> {
            });

            assertEquals(before + 1, family.getRegisteredListeners().length,
                    "with no getHandlerList to find, Bukkit walks up to the base and registers there");
            assertSame(family, new UndeclaredListEvent().getHandlers(), "and a dispatch walks up to the same place");
        }
    }

    @Test
    void aChildDeclaringNoListRegistersAndDispatchesThroughItsParentsOne() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            HandlerList parents = (HandlerList) ECEvent.getHandlerListOf(ParentDeclaringAListEvent.class);
            List<ParentDeclaringAListEvent> heard = new ArrayList<>();
            world.listen(ParentDeclaringAListEvent.class, heard::add);

            assertEquals(1, parents.getRegisteredListeners().length);
            assertSame(parents, new ChildOfADeclaredListEvent().getHandlers(),
                    "the child inherits the list the same way it inherits the registration");
            assertTrue(world.getAudience().hasListeners(ChildOfADeclaredListEvent.class),
                    "so a listener of the parent opens the gate of the child");

            ChildOfADeclaredListEvent posted = ECEventBus.global().post(new ChildOfADeclaredListEvent());

            assertEquals(1, heard.size(), "a listener of the parent hears the child through the bus");
            assertSame(posted, heard.get(0));
        }
    }

    @Test
    void theObjectTypedDeclarationWrittenAgainstTheStubIsTheOneBukkitResolves() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            //ECPluginReloadEvent is compiled where the base cannot name HandlerList, so its declaration
            //can only be typed Object - this is that class in front of the real plugin manager
            HandlerList own = (HandlerList) ECEvent.getHandlerListOf(ECPluginReloadEvent.class);
            int onTheFamilyList = ECEvent.getHandlerList().getRegisteredListeners().length;

            List<ECPluginReloadEvent> heard = new ArrayList<>();
            world.listen(ECPluginReloadEvent.class, heard::add);

            assertEquals(1, own.getRegisteredListeners().length, "an Object return type still registers");
            assertEquals(onTheFamilyList, ECEvent.getHandlerList().getRegisteredListeners().length,
                    "and nothing of it landed on the family fallback");

            ECPluginReloadEvent.Post posted = ECEventBus.global()
                    .post(new ECPluginReloadEvent.Post(world.getPluginData()));

            assertEquals(1, heard.size(), "the listener of the family base hears the half that was fired");
            assertSame(posted, heard.get(0));
        }
    }

    @Test
    void aDeclarationThatHandsBackNoListFallsBackToTheFamilyOneAndSaysSoOnce() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<String> logged = Logs.capture(() -> McHandlerLists.registrationListOf(NullListEvent.class));

            assertSame(ECEvent.getHandlerList(), McHandlerLists.registrationListOf(NullListEvent.class),
                    "an unusable declaration must not leave the event without a list");
            assertTrue(logged.stream().anyMatch(line -> line.contains("declares getHandlerList but it returned null")),
                    "the fallback is reported, never silent: " + logged);

            List<String> onTheSecondAsk = Logs.capture(() -> new NullListEvent().getHandlers());

            assertTrue(onTheSecondAsk.stream().noneMatch(line -> line.contains("declares getHandlerList")),
                    "said once per class, or every dispatch would repeat it: " + onTheSecondAsk);
        }
    }

    @Test
    void aDeclarationThatBlowsUpFallsBackToTheFamilyListToo() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<String> logged = Logs.capture(() -> new ThrowingListEvent().getHandlers());

            assertSame(ECEvent.getHandlerList(), McHandlerLists.registrationListOf(ThrowingListEvent.class));
            assertTrue(logged.stream().anyMatch(line -> line.contains("declares getHandlerList but invoking it failed")),
                    "the failure is reported with what it was: " + logged);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** The shape an event should have: one declaration, on the class listeners subscribe to. */
    static class DeclaredListEvent extends ECEvent {
        public static HandlerList getHandlerList() {
            return (HandlerList) ECEvent.getHandlerListOf(DeclaredListEvent.class);
        }
    }

    /** No declaration at all - the shape that shares the family list with every other one like it. */
    static class UndeclaredListEvent extends ECEvent {
    }

    /** A family base: the list is declared here, and the subtypes below stay on it. */
    static class ParentDeclaringAListEvent extends ECEvent {
        public static HandlerList getHandlerList() {
            return (HandlerList) ECEvent.getHandlerListOf(ParentDeclaringAListEvent.class);
        }
    }

    static class ChildOfADeclaredListEvent extends ParentDeclaringAListEvent {
    }

    /** Declared and static, but hands back nothing - Bukkit would cast that null and fail the registration. */
    static class NullListEvent extends ECEvent {
        public static HandlerList getHandlerList() {
            return null;
        }
    }

    /** Declared and static, and the invocation itself is what fails. */
    static class ThrowingListEvent extends ECEvent {
        public static HandlerList getHandlerList() {
            throw new IllegalStateException("this declaration is broken on purpose");
        }
    }

}
