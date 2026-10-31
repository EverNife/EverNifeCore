package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptKind;
import br.com.finalcraft.evernifecore.screen.prompt.PromptReply;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSession;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The page channel ({@link PromptKind#PAGE}): a page with the question, a text field, OK and Cancel takes the screen's
 * place. OK sends the field's text; a refusal shows the parser's text under the field. Cancel and the escape key close
 * the question. Serves every player who is in a world - the page is how the Hytale client shows anything.
 */
public final class HyPagePrompt implements PromptChannel {

    private final HyPageRenderer renderer;
    private final Map<PromptSession<?>, Page> pages = new ConcurrentHashMap<>();

    HyPagePrompt(@Nonnull HyPageRenderer renderer) {
        this.renderer = renderer;
    }

    @Nonnull
    @Override
    public PromptKind getKind() {
        return PromptKind.PAGE;
    }

    @Override
    public boolean isSupported(@Nonnull FPlayer player) {
        return renderer.getTransport().worldOf(player) != null;
    }

    @Override
    public void open(@Nonnull PromptSession<?> session) {
        FPlayer viewer = session.getPlayer();
        String world = renderer.getTransport().worldOf(viewer);
        if (world == null) {
            session.cancel();
            return;
        }
        Page page = new Page(renderer.getTransport().refOf(viewer), session, world);
        pages.put(session, page);
        renderer.show(viewer, world, page, session::cancel);
    }

    /**
     * The page's own close is silenced at once; the page itself goes only if nothing took its place by the next tick -
     * when the screen comes back, its page already replaced this one.
     */
    @Override
    public void dismiss(@Nonnull PromptSession<?> session) {
        Page page = pages.remove(session);
        if (page == null) {
            return;
        }
        page.markClosing(CloseReason.REQUESTED);
        renderer.getScheduler().later(1L, () -> renderer.closeIfShown(session.getPlayer(), page.world, page));
    }

    /** The question as a page. Its callbacks arrive on the world thread; the session hops on its own. */
    final class Page extends HyPage {

        private final PromptSession<?> session;
        private final String world;

        private Page(@Nullable PlayerRef playerRef, @Nonnull PromptSession<?> session, @Nonnull String world) {
            super(playerRef, CustomPageLifetime.CanDismiss);
            this.session = session;
            this.world = world;
        }

        @Override
        public void build(@Nullable Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                          @Nullable Store<EntityStore> store) {
            HyPageLayout.drawPrompt(ui, events, session.getSpec().getQuestion());
        }

        @Override
        public void handleDataEvent(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store, @Nonnull HyPageEvent event) {
            if (HyPageLayout.SUBMIT.equals(event.getKind())) {
                PromptReply reply = session.answer(event.getValue() == null ? "" : event.getValue());
                if (!reply.isAccepted()) {
                    UICommandBuilder ui = new UICommandBuilder();
                    HyPageLayout.promptError(ui, reply.getMessage());
                    renderer.onWorldThread(world, () -> renderer.getTransport().update(this, ui), () -> { });
                }
            } else if (HyPageLayout.CANCEL.equals(event.getKind())) {
                session.cancel();
            }
        }

        @Override
        public void onDismiss(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
            if (dismissedAs() == null) {
                session.cancel(); //the player closed it; a dismiss the core caused is already marked
            }
        }

    }

}
