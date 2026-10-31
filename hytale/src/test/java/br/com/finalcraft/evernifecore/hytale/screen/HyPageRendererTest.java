package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.MutableState;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenFallback;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the page renderer does with the server: when it opens, what a click and a patch become, how a page goes away. */
class HyPageRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void opensThePageOnlyThroughTheViewersWorldThreadWhenCalledOffWorldThread() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir, Platforms::offWorldThread)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            assertTrue(!pages.screens.getPlatform().isWorldThread(RecordingPageTransport.WORLD), "the test runs off every world thread");
            List<String> hops = pages.screens.getPlatform().getWorldThreadRuns();
            int before = hops.size();
            ScreenView view = pages.screens.open(Screen.of(1), steve);

            RecordingPageTransport.Opened opened = pages.transport.lastOpened();
            assertEquals(before + 1, opened.worldHopsAtOpen, "the page manager was reached outside a world-thread hop");
            assertEquals(RecordingPageTransport.WORLD, hops.get(before), "the hop went to another world");
            assertSame(view.getSurface(), pages.transport.pageOf(steve));
            close(pages, view);
        }
    }

    @Test
    void aViewerOutsideAnyWorldIsNotServed() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            pages.transport.leaveWorld(steve);
            CompletableFuture<ScreenView> opening = pages.screens.tryOpen(Screen.of(1), steve);
            assertTrue(opening.isCompletedExceptionally());
            assertTrue(pages.transport.getOpened().isEmpty());
        }
    }

    @Test
    void aPageTheServerCouldNotShowClosesTheViewAsDisconnected() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            List<CloseReason> reasons = new ArrayList<>();
            pages.transport.refuseOpens(true);
            ScreenView view = pages.screens.open(Screen.of(1).onClose(context -> reasons.add(context.getReason())),
                    pages.screens.viewer("Steve"));
            pages.screens.advanceTicks(1);
            assertTrue(view.isClosed());
            assertEquals(List.of(CloseReason.DISCONNECTED), reasons);
        }
    }

    @Test
    void aClickOnACellReachesTheElementOnThatSlotWithItsGesture() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            List<ClickGesture> heard = new ArrayList<>();
            Screen<?> screen = Screen.of(1).debounce(0); //three clicks in one millisecond
            screen.icon(4, Icon.of(ItemSpec.of("hytale:Rock_Stone", 1))).id("probe").onClick(context -> heard.add(context.getGesture()));
            ScreenView view = pages.screens.open(screen, steve);

            pages.transport.click(steve, 4, "Activating");
            pages.transport.click(steve, 4, "RightClicking");
            pages.transport.click(steve, 4, "DoubleClicking");
            pages.transport.click(steve, 3, "Activating");

            assertEquals(List.of(ClickGesture.PRIMARY, ClickGesture.SECONDARY, ClickGesture.DOUBLE), heard);
            close(pages, view);
        }
    }

    @Test
    void aStateChangeIsSentAsAnUpdateOfOnlyTheCellThatShowsIt() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            MutableState<Integer> stock = new MutableState<>(1);
            Screen<?> screen = Screen.of(1);
            screen.icon(2, Icon.of(ItemSpec.of("hytale:Rock_Stone", 1))).id("stock").dependsOn(stock).render(i -> i.amount(stock.get()));
            screen.icon(6, Icon.of(ItemSpec.of("hytale:Rock_Stone", 1))).id("still");
            ScreenView view = pages.screens.open(screen, pages.screens.viewer("Steve"));
            pages.transport.getUpdates().clear();

            stock.set(7);
            pages.screens.advanceTicks(1);

            assertEquals(1, pages.transport.getUpdates().size(), "one update for one tick");
            for (CustomUICommand command : pages.transport.getUpdates().get(0)) {
                assertTrue(command.selector.startsWith("#Grid[2] "), command.selector);
            }
            close(pages, view);
        }
    }

    @Test
    void escapeClosesTheScreenAsPlayerClosed() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            List<CloseReason> reasons = new ArrayList<>();
            ScreenView view = pages.screens.open(Screen.of(1).onClose(context -> reasons.add(context.getReason())), steve);
            pages.transport.dismiss(steve);
            assertTrue(view.isClosed());
            assertEquals(List.of(CloseReason.PLAYER_CLOSED), reasons);
        }
    }

    @Test
    void aServerCloseTakesThePageAwayAsRequested() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            List<CloseReason> reasons = new ArrayList<>();
            ScreenView view = pages.screens.open(Screen.of(1).onClose(context -> reasons.add(context.getReason())), steve);
            view.close();
            assertNull(pages.transport.pageOf(steve));
            assertEquals(List.of(CloseReason.REQUESTED), reasons);
        }
    }

    @Test
    void anotherScreenOpenedOverThePageClosesTheFirstAsRequested() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            List<CloseReason> reasons = new ArrayList<>();
            ScreenView first = pages.screens.open(Screen.of(1).onClose(context -> reasons.add(context.getReason())), steve);
            ScreenView second = pages.screens.open(Screen.of(2), steve);
            assertTrue(first.isClosed());
            assertEquals(List.of(CloseReason.REQUESTED), reasons);
            assertSame(second.getSurface(), pages.transport.pageOf(steve));
            close(pages, second);
        }
    }

    @Test
    void playerQuitClosesAsPlayerQuit() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            List<CloseReason> reasons = new ArrayList<>();
            ScreenView view = pages.screens.open(Screen.of(1).onClose(context -> reasons.add(context.getReason())), steve);

            pages.renderer.onDisconnect(new PlayerDisconnectEvent(new PlayerRef(null, steve.getUniqueId(), "Steve", "en_us", null, null)));

            assertTrue(view.isClosed());
            assertEquals(List.of(CloseReason.DISCONNECTED), reasons, "the core's name for a player quitting");
        }
    }

    @Test
    void storageSlotsUnsupported() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            Screen<?> editable = Screen.of(1);
            editable.storage(Slots.of(0));

            CompletableFuture<ScreenView> refused = pages.screens.tryOpen(editable, steve);
            Throwable why = assertInstanceOf(Throwable.class, refused.handle((view, error) -> error).join());
            String message = why.getMessage();
            assertTrue(message.contains("STORAGE_SLOTS") && message.contains(HyPageRenderer.ID), message);
            assertTrue(message.contains("onMissingCapability"), "the refusal teaches the way out: " + message);

            Screen<?> readOnly = Screen.of(1).title("Read only");
            Screen<?> withAlternative = Screen.of(1);
            withAlternative.storage(Slots.of(0));
            withAlternative.onMissingCapability(ScreenFallback.open(readOnly));
            ScreenView view = pages.screens.open(withAlternative, steve);
            assertSame(readOnly, view.getScreen());
            close(pages, view);
        }
    }

    private static void close(HyPageWorld pages, ScreenView view) {
        view.close();
        pages.screens.advanceTicks(1);
    }

}
