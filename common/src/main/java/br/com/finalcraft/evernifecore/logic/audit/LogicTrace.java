package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Who is watching whose logic: the state behind {@code /ecore logic trace <player> [plugin]}. Every line a gate, a
 * cost, an effect or a multiplier writes for a context of the traced player - optionally only of one plugin's callers -
 * goes to each watcher, with the caller in front; the same line goes to the log while {@link ECDebugModule#LOGIC} is on.
 * A watcher that is a player stops watching when it quits. Thread-safe.
 */
public final class LogicTrace {

    private static final Map<String, Watch> WATCHES = new ConcurrentHashMap<>();
    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> quitSubscription;

    private LogicTrace() {
    }

    /** One watcher's trace: whose contexts, which plugin's callers ({@code null} for all), and where the lines go. */
    public static final class Watch {
        private final UUID player;
        private final @Nullable String plugin;
        private final Consumer<String> sink;

        Watch(UUID player, @Nullable String plugin, Consumer<String> sink) {
            this.player = player;
            this.plugin = plugin;
            this.sink = sink;
        }

        public UUID getPlayer() {
            return player;
        }

        public @Nullable String getPlugin() {
            return plugin;
        }

        boolean matches(ExecutionContext ctx) {
            FPlayer target = ctx.getPlayer();
            return target != null && player.equals(target.getUniqueId())
                    && (plugin == null || plugin.equalsIgnoreCase(ctx.getCaller().getPlugin().getMetaInfo().getName()));
        }
    }

    /**
     * {@code watcherId} (a player's uuid, or any other stable name for the console or a test) now receives the lines of
     * {@code player}'s contexts - only those of {@code plugin}'s callers when it is not null - replacing what it watched.
     */
    public static void start(String watcherId, UUID player, @Nullable String plugin, Consumer<String> sink) {
        WATCHES.put(watcherId, new Watch(player, plugin, sink));
        synchronized (LogicTrace.class) {
            if (quitSubscription == null) {
                quitSubscription = ECEventBus.global().subscribe(ECPlayerQuitEvent.class,
                        event -> stop(event.getPlayerData().getUniqueId().toString()));
            }
        }
    }

    /** {@code watcherId} stops watching; true when it was. */
    public static boolean stop(String watcherId) {
        boolean was = WATCHES.remove(watcherId) != null;
        synchronized (LogicTrace.class) {
            if (WATCHES.isEmpty() && quitSubscription != null) {
                quitSubscription.unsubscribe();
                quitSubscription = null;
            }
        }
        return was;
    }

    /** What {@code watcherId} watches, empty when nothing. */
    public static Optional<Watch> get(String watcherId) {
        return Optional.ofNullable(WATCHES.get(watcherId));
    }

    /** Sends {@code [caller] text} to every watcher of {@code ctx} and to the {@code LOGIC} debug log; builds it only when someone reads it. */
    public static void publish(ExecutionContext ctx, Supplier<String> text) {
        String line = null;
        for (Watch watch : WATCHES.values()) {
            if (watch.matches(ctx)) {
                if (line == null) {
                    line = "[" + ctx.getCaller() + "] " + text.get();
                }
                watch.sink.accept(line);
            }
        }
        //off by default: asking the module first keeps a gate from reading the debug config on its hot path
        if (ECDebugModule.LOGIC.isEnabled()) {
            String built = line;
            ECDebugModule.LOGIC.debug(() -> built != null ? built : "[" + ctx.getCaller() + "] " + text.get());
        }
    }
}
