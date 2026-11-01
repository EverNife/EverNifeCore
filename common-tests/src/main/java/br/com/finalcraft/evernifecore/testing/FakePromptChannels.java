package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptKind;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.screen.prompt.PromptReply;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSession;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt channels a test drives, registered in the real {@link PromptRegistry} by the {@code test} plugin of a
 * {@link ScreenWorld} ({@link ScreenWorld#prompts()}): each records what it opened and dismissed, and the test types or
 * closes the window. A {@link ScreenWorld} starts with one {@link PromptKind#CHAT} channel that serves everybody.
 */
public final class FakePromptChannels {

    private final ECPluginData owner;
    private final RecordingRenderer renderer;
    private final Map<PromptKind, FakePromptChannel> channels = new EnumMap<>(PromptKind.class);
    private final List<PromptSession<?>> opened = new ArrayList<>();

    FakePromptChannels(@Nonnull ECPluginData owner, @Nonnull RecordingRenderer renderer) {
        this.owner = owner;
        this.renderer = renderer;
    }

    /** A fake channel for {@code kind}, in place of the one this world had for it; {@code supports} answers every player. */
    @Nonnull
    public FakePromptChannel register(@Nonnull PromptKind kind, boolean supports) {
        if (channels.remove(kind) != null) {
            PromptRegistry.get().unregister(owner, kind);
        }
        FakePromptChannel channel = new FakePromptChannel(kind, supports);
        PromptRegistry.get().register(owner, channel);
        channels.put(kind, channel);
        return channel;
    }

    /** Takes every fake channel out - another platform's channels are about to answer instead. */
    @Nonnull
    public FakePromptChannels clear() {
        PromptRegistry.get().unregisterAll(owner);
        channels.clear();
        return this;
    }

    /** The fake channel for {@code kind}, or {@code null}. */
    @Nullable
    public FakePromptChannel get(@Nonnull PromptKind kind) {
        return channels.get(kind);
    }

    /** The question opened last on any fake channel, ended or not. */
    @Nonnull
    public PromptSession<?> lastOpened() {
        if (opened.isEmpty()) {
            throw new IllegalStateException("No fake channel has opened a question yet. Ask, then advance a tick: the "
                    + "registry arms a question one tick after it is asked.");
        }
        return opened.get(opened.size() - 1);
    }

    /** The player typing {@code text} into the question opened last. */
    @Nonnull
    public PromptReply type(@Nonnull String text) {
        return lastOpened().answer(text);
    }

    /** The player closing the window of the question opened last. */
    public void closeWindow() {
        lastOpened().cancel();
    }

    /** One fake channel: what it opened and dismissed, in order. */
    public final class FakePromptChannel implements PromptChannel {

        private final PromptKind kind;
        private volatile boolean supports;
        private final List<PromptSession<?>> openedHere = new ArrayList<>();
        private final List<PromptSession<?>> dismissed = new ArrayList<>();

        private FakePromptChannel(PromptKind kind, boolean supports) {
            this.kind = kind;
            this.supports = supports;
        }

        public void setSupported(boolean supports) {
            this.supports = supports;
        }

        @Nonnull
        @Override
        public PromptKind getKind() {
            return kind;
        }

        @Override
        public boolean isSupported(@Nonnull FPlayer player) {
            return supports;
        }

        /** Takes the screen's recorded window away without a report, as a real channel's own window would replace it. */
        @Override
        public void open(@Nonnull PromptSession<?> session) {
            for (int i = renderer.getSurfaces().size() - 1; i >= 0; i--) {
                RecordingSurface surface = renderer.getSurfaces().get(i);
                if (!surface.isClosed() && surface.getView().getViewerId().equals(session.getPlayer().getUniqueId())) {
                    surface.dropSilently();
                    break;
                }
            }
            openedHere.add(session);
            opened.add(session);
        }

        @Override
        public void dismiss(@Nonnull PromptSession<?> session) {
            dismissed.add(session);
        }

        @Nonnull
        public List<PromptSession<?>> getOpened() {
            return Collections.unmodifiableList(openedHere);
        }

        @Nonnull
        public List<PromptSession<?>> getDismissed() {
            return Collections.unmodifiableList(dismissed);
        }

    }

}
