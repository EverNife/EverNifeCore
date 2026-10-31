package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickKind;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.ScreenSurface;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * One {@link ScreenView} drawn as a page: the window the view writes through, and the page the server shows.
 *
 * <p>It keeps what each cell shows, because the page is built on the world thread whenever the server gets to it,
 * while the view writes on the screen thread from the moment the window was promised. {@code build} draws whatever the
 * cells hold then; a patch that comes after is sent as an update of only the cells it names.</p>
 */
final class HyScreenPage extends HyPage implements ScreenSurface {

    private final HyPageRenderer renderer;
    private final ScreenView view;
    private final FPlayer viewer;
    private final ElementPatch[] cells;
    private final String world;
    private String title;
    private boolean built;

    HyScreenPage(@Nullable PlayerRef playerRef, @Nonnull HyPageRenderer renderer, @Nonnull ScreenView view, @Nonnull String world) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.renderer = renderer;
        this.view = view;
        this.viewer = view.getViewer();
        this.world = world;
        this.cells = new ElementPatch[view.getGrid().getSize()];
        this.title = view.getCurrentTitle();
    }

    @Nonnull
    ScreenView getView() {
        return view;
    }

    /** Kept apart from the view, which forgets its viewer once released - and a close comes after that. */
    @Nonnull
    FPlayer getViewer() {
        return viewer;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The server's side - world thread
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void build(@Nullable Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                      @Nullable Store<EntityStore> store) {
        synchronized (this) {
            HyPageLayout.draw(ui, events, title, view.getGrid(), cells.clone());
            built = true;
        }
    }

    @Override
    public void handleDataEvent(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store, @Nonnull HyPageEvent event) {
        ClickGesture gesture = HyPageLayout.gestureOf(event.getKind());
        int slot;
        try {
            slot = Integer.parseInt(String.valueOf(event.getSlot()));
        } catch (NumberFormatException notACell) {
            return;
        }
        renderer.getScheduler().run(() -> {
            //a click that left the client before this page was replaced belongs to nobody now
            if (view.isSurface(this)) {
                view.click(slot, gesture, ClickKind.NOTHING, null);
            }
        });
    }

    @Override
    public void onDismiss(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
        CloseReason reason = dismissedAs() != null ? dismissedAs() : CloseReason.PLAYER_CLOSED;
        renderer.getScheduler().run(() -> {
            //a page torn down after its view moved on to another (resumed, re-titled) is not that view closing
            if (view.isSurface(this)) {
                Screens.windowClosed(view, reason);
            }
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The view's side - screen thread
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void apply(@Nonnull List<ElementPatch> patch) {
        UICommandBuilder ui = new UICommandBuilder();
        boolean send;
        synchronized (this) {
            for (ElementPatch each : patch) {
                if (each.getSlot() >= 0 && each.getSlot() < cells.length) {
                    cells[each.getSlot()] = each;
                    HyPageLayout.cell(ui, each.getSlot(), each);
                }
            }
            send = built;
        }
        if (send) {
            renderer.onWorldThread(world, () -> renderer.getTransport().update(this, ui), () -> { });
        }
    }

    @Override
    public void setTitle(@Nonnull String title) {
        UICommandBuilder ui = new UICommandBuilder();
        boolean send;
        synchronized (this) {
            this.title = title;
            HyPageLayout.title(ui, title);
            send = built;
        }
        if (send) {
            renderer.onWorldThread(world, () -> renderer.getTransport().update(this, ui), () -> { });
        }
    }

    @Override
    public void close(@Nonnull CloseReason reason) {
        markClosing(reason);
        //already off the screen (replaced, never shown) or its world gone: no dismissal will come to report it
        Runnable reportNow = () -> renderer.getScheduler().run(() -> Screens.windowClosed(view, reason));
        renderer.onWorldThread(world, () -> {
            if (!renderer.getTransport().close(viewer, this)) {
                reportNow.run();
            }
        }, reportNow);
    }

    @Override
    public boolean isBackedBy(@Nullable Object nativeWindow) {
        return nativeWindow == this;
    }

    @Override
    public String toString() {
        return "HyScreenPage{" + view + "}";
    }

}
