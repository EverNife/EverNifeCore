package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Hytale plugin closes every screen from its shutdown thread, while each screen lives on the screen thread of its
 * own - so {@link Screens#closeAll()} has to take the close there and wait for it.
 */
class HyScreensShutdownTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void closeAllFromAnotherThreadRunsEachOnCloseOnTheScreenThreadAndReturnsWithTheViewClosed() throws Exception {
        try (HyPageWorld world = HyPageWorld.onTheScreenThread(tempDir)) {
            FPlayer alex = world.screens.viewer("Alex");
            CompletableFuture<Boolean> closedOnScreenThread = new CompletableFuture<>();
            CompletableFuture<CloseReason> closedWith = new CompletableFuture<>();
            Screen<Layout> screen = Screen.of(3).onClose(close -> {
                closedOnScreenThread.complete(HyScreenScheduler.INSTANCE.isRenderThread());
                closedWith.complete(close.getReason());
            });
            ScreenView view = screen.open(alex).get(5, TimeUnit.SECONDS);

            CompletableFuture<Boolean> closedWhenItReturned = new CompletableFuture<>();
            Thread shutdown = new Thread(() -> {
                Screens.closeAll();
                closedWhenItReturned.complete(view.isClosed());
            }, "Hytale shutdown");
            shutdown.start();
            shutdown.join(10_000L);

            assertTrue(closedWhenItReturned.getNow(false), "closeAll waits for the screens it closes");
            assertTrue(closedOnScreenThread.get(1, TimeUnit.SECONDS), "onClose ran where the screen lives, not on the caller's thread");
            assertEquals(CloseReason.SHUTDOWN, closedWith.get(1, TimeUnit.SECONDS));
        }
    }
}
