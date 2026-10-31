package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenRenderer;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import br.com.finalcraft.evernifecore.screen.ScreenSurface;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Hytale renderer: every screen is a server page ({@code InteractiveCustomUIPage}) drawn from the core's own
 * layouts - a frame, the title, one cell per slot with the item, its amount and a tooltip. Priority 0, so a renderer a
 * client mod registers wins for the players who have it. Questions are asked on a page too ({@link HyPagePrompt}).
 *
 * <p>Screens live on their own thread ({@link HyScreenScheduler}); the page lives on the viewer's world thread. Every
 * call into the server hops there through {@code IPlatform.runOnWorldThread}, and every click and dismissal hops back.
 * So {@link #open} promises the window before the server has shown it: when the player turns out to be gone by then,
 * the view is closed as {@link CloseReason#DISCONNECTED}.</p>
 *
 * <p>A page shows items; it does not move items between the player and a container, so {@code STORAGE_SLOTS} is not
 * declared and a screen with an editable area is refused here, naming the capability - or opens its
 * {@code onMissingCapability} alternative. A cell has no shift or middle click: the page reports primary, secondary and
 * double clicks only.</p>
 */
public final class HyPageRenderer implements ScreenRenderer {

    public static final String ID = "evernifecore:page";

    private static final Set<RendererCapability> CAPABILITIES = Collections.unmodifiableSet(EnumSet.of(RendererCapability.TITLE_CHANGE));

    private final HyPageTransport transport;
    private final ScreenScheduler scheduler;
    private final HyPagePrompt prompts;
    /** The page each viewer was last shown by this renderer, so the one it replaces knows it was replaced. */
    private final Map<UUID, HyPage> shown = new ConcurrentHashMap<>();

    public HyPageRenderer() {
        this(HyPageTransport.PAGE_MANAGER, HyScreenScheduler.INSTANCE);
    }

    HyPageRenderer(@Nonnull HyPageTransport transport, @Nonnull ScreenScheduler scheduler) {
        this.transport = transport;
        this.scheduler = scheduler;
        this.prompts = new HyPagePrompt(this);
    }

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Nonnull
    @Override
    public Set<RendererCapability> getCapabilities() {
        return CAPABILITIES;
    }

    /** A Hytale player standing in a world - not one offline or between worlds. */
    @Override
    public boolean supports(@Nonnull FPlayer viewer, @Nonnull Screen<?> screen) {
        return transport.worldOf(viewer) != null;
    }

    @Nullable
    @Override
    public ScreenSurface open(@Nonnull ScreenView view) {
        FPlayer viewer = view.getViewer();
        String world = viewer == null ? null : transport.worldOf(viewer);
        if (world == null) {
            return null;
        }
        HyScreenPage page = new HyScreenPage(transport.refOf(viewer), this, view, world);
        show(viewer, world, page, () -> Screens.windowClosed(view, CloseReason.DISCONNECTED));
        return page;
    }

    @Nonnull
    @Override
    public ScreenScheduler getScheduler() {
        return scheduler;
    }

    /** The page channel this renderer's pages ask through; the platform registers it in {@code PromptRegistry}. */
    @Nonnull
    public HyPagePrompt getPromptChannel() {
        return prompts;
    }

    /**
     * The player left: whatever screen or question they had ends as {@link CloseReason#DISCONNECTED} /
     * {@code QUIT}. Registered on the server's {@code PlayerDisconnectEvent}; any thread.
     */
    public void onDisconnect(@Nonnull PlayerDisconnectEvent event) {
        UUID viewerId = event.getPlayerRef().getUuid();
        scheduler.run(() -> {
            shown.remove(viewerId);
            Screens.viewerGone(viewerId, CloseReason.DISCONNECTED);
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the pages share
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    HyPageTransport getTransport() {
        return transport;
    }

    /**
     * Shows {@code page} over whatever {@code viewer} has, on their world thread; {@code refused} runs on the screen
     * thread when the server could not show it. Screen thread.
     */
    void show(@Nonnull FPlayer viewer, @Nonnull String world, @Nonnull HyPage page, @Nonnull Runnable refused) {
        HyPage previous = shown.put(viewer.getUniqueId(), page);
        if (previous != null && previous != page) {
            previous.markClosing(CloseReason.REQUESTED);
        }
        //later, never in place: a refusal must find the open that caused it finished and the view registered
        onWorldThread(world, () -> {
            if (!transport.open(viewer, page)) {
                scheduler.later(0L, refused);
            }
        }, () -> scheduler.later(0L, refused));
    }

    /** Closes {@code page} when it is still the one {@code viewer} was last shown - nothing took its place. Screen thread. */
    void closeIfShown(@Nonnull FPlayer viewer, @Nonnull String world, @Nonnull HyPage page) {
        if (shown.remove(viewer.getUniqueId(), page)) {
            onWorldThread(world, () -> transport.close(viewer, page), () -> { });
        }
    }

    /** Runs {@code task} on {@code world}'s thread; {@code failed} when that world has no thread any more. */
    void onWorldThread(@Nonnull String world, @Nonnull Runnable task, @Nonnull Runnable failed) {
        EverNifeCore.getPlatform().runOnWorldThread(world, task).whenComplete((done, error) -> {
            if (error != null) {
                failed.run();
            }
        });
    }

}
