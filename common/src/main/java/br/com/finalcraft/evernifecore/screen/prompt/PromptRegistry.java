package br.com.finalcraft.evernifecore.screen.prompt;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.screen.Cancellable;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one place a question is kept while it is out, whatever window it goes through: at most one per screen, the
 * screen set aside while it is out and given back once it ends, the timeout, the second question that calls the first
 * off, and the screen released mid-question. The {@link PromptChannel}s only show the question.
 *
 * <p>Everything that touches a screen runs on that screen's thread ({@link ScreenView#getScheduler()}): the main thread
 * on Bukkit, the screen thread on Hytale. A channel may report from any thread.</p>
 */
public final class PromptRegistry {

    private static final PromptRegistry INSTANCE = new PromptRegistry();

    /** The registry's own refusal text when a parser threw without a message: a blank refusal looks like a frozen window. */
    static final String REFUSED_WITHOUT_REASON = "§cThat answer was not accepted - try again.";

    private final Map<PromptKind, Registered> channels = new EnumMap<>(PromptKind.class);
    private final Map<ScreenView, Session<?>> pending = new ConcurrentHashMap<>();
    private volatile boolean warnedNoChannel;

    private PromptRegistry() {

    }

    /** One per JVM, like {@code ECEventBus.global()}. */
    @Nonnull
    public static PromptRegistry get() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Channels
    // -----------------------------------------------------------------------------------------------------------------

    /** Adds {@code channel} for its kind; a kind already taken refuses, naming both. Platform wiring, any thread. */
    public synchronized void register(@Nonnull ECPluginData owner, @Nonnull PromptChannel channel) {
        PromptKind kind = channel.getKind();
        Registered taken = channels.get(kind);
        if (taken != null) {
            throw new IllegalStateException("A PromptChannel for " + kind + " is already registered by ["
                    + taken.owner.getMetaInfo().getName() + "] (" + nameOf(taken.channel) + "); ["
                    + owner.getMetaInfo().getName() + "] tried to add [" + nameOf(channel) + "]. One channel "
                    + "per kind: unregister the first, or give yours another PromptKind.");
        }
        channels.put(kind, new Registered(owner, channel));
        warnedNoChannel = false;
    }

    /** Takes out {@code owner}'s channel for {@code kind}; another owner's is left alone. */
    public synchronized void unregister(@Nonnull ECPluginData owner, @Nonnull PromptKind kind) {
        Registered registered = channels.get(kind);
        if (registered != null && registered.owner == owner) {
            channels.remove(kind);
        }
    }

    /** Takes out every channel {@code owner} registered. A question already out keeps its channel until it ends. */
    public synchronized void unregisterAll(@Nonnull ECPluginData owner) {
        channels.values().removeIf(registered -> registered.owner == owner);
    }

    /** The channel registered for {@code kind}, or {@code null}. */
    @Nullable
    public synchronized PromptChannel getChannel(@Nonnull PromptKind kind) {
        Registered registered = channels.get(kind);
        return registered == null ? null : registered.channel;
    }

    /**
     * The kind {@link #ask} would use for {@code player}: {@code preferred} when registered and it serves them, else the
     * first registered in {@link PromptKind} order that does; {@code null} when none does. Screen thread.
     */
    @Nullable
    public PromptKind resolve(@Nonnull FPlayer player, @Nullable PromptKind preferred) {
        PromptChannel channel = channelFor(player, preferred);
        return channel == null ? null : channel.getKind();
    }

    @Nullable
    private synchronized PromptChannel channelFor(FPlayer player, @Nullable PromptKind preferred) {
        if (preferred != null && serves(channels.get(preferred), player)) {
            return channels.get(preferred).channel;
        }
        for (PromptKind kind : PromptKind.values()) {
            if (serves(channels.get(kind), player)) {
                return channels.get(kind).channel;
            }
        }
        return null;
    }

    private static boolean serves(@Nullable Registered registered, FPlayer player) {
        if (registered == null) {
            return false;
        }
        try {
            return registered.channel.isSupported(player);
        } catch (Throwable broken) {
            EverNifeCore.getLog().severe("The PromptChannel [{}] failed to say whether it serves [{}]",
                    nameOf(registered.channel), player.getName(), broken);
            return false;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Asking
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Asks {@code view}'s player. One tick later (a click is still being answered when this runs) the screen is set
     * aside and the chosen channel shows the question; once it ends the screen is given back - before the future
     * completes, so what the caller chains may draw - and the future completes on the screen's thread, never
     * exceptionally. A second question on the same screen ends the first as {@code CANCELLED} and takes the screen over
     * as it is. No channel for the player: {@code CANCELLED}, with the wiring error logged. Any thread.
     */
    @Nonnull
    public <T> CompletableFuture<PromptResult<T>> ask(@Nonnull ScreenView view, @Nonnull PromptSpec<T> spec) {
        CompletableFuture<PromptResult<T>> answer = new CompletableFuture<>();
        view.getScheduler().later(1L, () -> arm(view, spec, answer));
        return answer;
    }

    private <T> void arm(ScreenView view, PromptSpec<T> spec, CompletableFuture<PromptResult<T>> answer) {
        FPlayer player = view.getViewer();
        if (player == null || !player.isOnline() || view.isClosed()) {
            answer.complete(PromptResult.<T>quit());
            return;
        }
        PromptChannel channel = channelFor(player, spec.getPreferredKind());
        if (channel == null) {
            warnNoChannel(player);
            answer.complete(PromptResult.<T>cancelled());
            return;
        }

        //the question called off is the one that set this screen aside, so it stays aside and this one takes it over;
        //suspending again would refuse (no window) and strand the screen with nothing out to bring it back
        Session<?> previous = pending.remove(view);
        if (previous != null) {
            previous.decide(PromptResult.cancelled());
            end(previous, false);
        } else if (!ScreenNavigation.suspend(view)) {
            answer.complete(PromptResult.<T>quit());
            return;
        }

        Session<T> session = new Session<>(view, player, spec, channel, answer);
        pending.put(view, session);
        ScreenScheduler scheduler = view.getScheduler();
        session.timeout = scheduler.later(scheduler.ticksOf(spec.getTimeoutMillis()), () -> {
            if (session.decide(PromptResult.<T>timeout())) {
                end(session, true);
            }
        });
        try {
            channel.open(session);
        } catch (Throwable broken) {
            EverNifeCore.getLog().severe("The PromptChannel [{}] failed to ask [{}]; the question is called off",
                    nameOf(channel), player.getName(), broken);
            if (session.decide(PromptResult.<T>cancelled())) {
                end(session, true);
            }
        }
    }

    private void warnNoChannel(FPlayer player) {
        if (warnedNoChannel) {
            return;
        }
        warnedNoChannel = true;
        String why;
        synchronized (this) {
            why = channels.isEmpty() ? "none is registered" : "none of " + channels.keySet() + " supports them";
        }
        EverNifeCore.getLog().severe("No PromptChannel can ask [" + player.getName() + "] anything: " + why + ". The "
                + "platform registers its channels in onECPluginEnablePost - on Bukkit the core registers ANVIL and CHAT, "
                + "on Hytale PAGE; a plugin may register its own with PromptRegistry.get().register(...).");
    }

    /** The kind of the question {@code view} has out, or {@code null}. Any thread. */
    @Nullable
    public PromptKind getPending(@Nonnull ScreenView view) {
        Session<?> session = pending.get(view);
        return session == null ? null : session.channel.getKind();
    }

    /** How many questions are out right now, on every screen. */
    public int getPendingCount() {
        return pending.size();
    }

    /**
     * Internal - called by {@code ScreenView} while it is released (quit, discard, shutdown): a question still out ends
     * as {@code QUIT}, its channel takes the window away, and nothing is given back. Screen thread.
     */
    public void abandon(@Nonnull ScreenView view) {
        Session<?> session = pending.remove(view);
        if (session != null) {
            session.decide(PromptResult.quit());
            end(session, false);
        }
    }

    /**
     * Ends a session whose outcome is decided: timeout cancelled, window dismissed, the spec's handler run, the screen
     * given back (when {@code giveScreenBack} and the outcome is not {@code QUIT}), then the future. Screen thread.
     */
    private <T> void end(Session<T> session, boolean giveScreenBack) {
        if (session.finished) {
            return; //taken over or abandoned while its own ending waited for the next tick
        }
        session.finished = true;
        pending.remove(session.view, session);
        if (session.timeout != null) {
            session.timeout.cancel();
        }
        try {
            session.channel.dismiss(session);
        } catch (Throwable broken) {
            EverNifeCore.getLog().severe("The PromptChannel [{}] failed to take its window away from [{}]",
                    nameOf(session.channel), session.player.getName(), broken);
        }
        PromptResult<T> result = session.outcome.get();
        if (result.getKind() == PromptResult.Kind.QUIT) {
            if (session.spec.getOnQuit() != null) {
                session.spec.getOnQuit().run();
            }
            session.answer.complete(result);
            return; //there is nobody left to show the screen to
        }
        if (result.getKind() == PromptResult.Kind.TIMEOUT && session.spec.getOnTimeout() != null) {
            session.spec.getOnTimeout().accept(session.view);
        }
        if (giveScreenBack && !ScreenNavigation.resume(session.view)) {
            //the screen could not be given back (the platform refused the window, or the player is gone): a chain left
            //behind would keep the suspended views and their tasks alive
            ScreenNavigation.discard(session.view.getViewerId());
        }
        session.answer.complete(result);
    }

    /** The simple name, or the full one for an anonymous class, whose simple name is empty. */
    private static String nameOf(PromptChannel channel) {
        String simple = channel.getClass().getSimpleName();
        return simple.isEmpty() ? channel.getClass().getName() : simple;
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static final class Registered {

        private final ECPluginData owner;
        private final PromptChannel channel;

        private Registered(ECPluginData owner, PromptChannel channel) {
            this.owner = owner;
            this.channel = channel;
        }

    }

    private final class Session<T> implements PromptSession<T> {

        private final ScreenView view;
        private final FPlayer player;
        private final PromptSpec<T> spec;
        private final PromptChannel channel;
        private final CompletableFuture<PromptResult<T>> answer;
        /** Set once, by whichever ending gets there first; every later one is ignored. */
        private final AtomicReference<PromptResult<T>> outcome = new AtomicReference<>();
        private Cancellable timeout;
        /** Screen thread only. */
        private boolean finished;

        private Session(ScreenView view, FPlayer player, PromptSpec<T> spec, PromptChannel channel,
                        CompletableFuture<PromptResult<T>> answer) {
            this.view = view;
            this.player = player;
            this.spec = spec;
            this.channel = channel;
            this.answer = answer;
        }

        @SuppressWarnings("unchecked")
        private boolean decide(PromptResult<?> result) {
            return outcome.compareAndSet(null, (PromptResult<T>) result);
        }

        /**
         * Ends it from a channel's report, one tick later on the screen's thread: the report may come from inside an
         * inventory event, where opening the screen's window again is what a server must not be asked to do.
         */
        private void settleLater(PromptResult<T> result) {
            if (decide(result)) {
                view.getScheduler().later(1L, () -> end(this, true));
            }
        }

        @Nonnull
        @Override
        public FPlayer getPlayer() {
            return player;
        }

        @Nonnull
        @Override
        public PromptSpec<T> getSpec() {
            return spec;
        }

        @Nonnull
        @Override
        public PromptReply answer(@Nonnull String typed) {
            if (!isOpen()) {
                return PromptReply.accepted();
            }
            String text = typed == null ? "" : typed.trim();
            String cancelWord = spec.getCancelWord();
            if (!cancelWord.isEmpty() && text.equalsIgnoreCase(cancelWord)) {
                settleLater(PromptResult.<T>cancelled());
                return PromptReply.accepted();
            }
            T parsed;
            try {
                parsed = spec.getParser().parse(text);
            } catch (Throwable refused) {
                String why = refused.getMessage();
                return PromptReply.refused(why == null || why.trim().isEmpty() ? REFUSED_WITHOUT_REASON : why);
            }
            settleLater(PromptResult.value(parsed));
            return PromptReply.accepted();
        }

        @Override
        public void cancel() {
            settleLater(PromptResult.<T>cancelled());
        }

        @Override
        public boolean isOpen() {
            return outcome.get() == null;
        }

        @Override
        public String toString() {
            return "PromptSession{" + channel.getKind() + ", " + player.getName() + ", " + spec.getQuestion() + "}";
        }

    }

}
