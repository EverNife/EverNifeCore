package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

import java.util.function.BiConsumer;

/**
 * The plugin whose feature is running on this thread, so a message of EverNifeCore sent inside it speaks that plugin's
 * language instead of the core's: a core node mounted on a plugin's command, a core parser refusing an argument, the
 * help of that command, a click on that plugin's screen. Entered by the command dispatch (run, tab, help) and by a
 * screen's open, redraw and click; a message of any other plugin ignores it, and a player's own language still wins
 * when per-player locale is on.
 *
 * <p>The scope belongs to the thread that entered it: work that runs later (a {@code whenComplete} on another thread)
 * does not inherit it. Such a reply carries it explicitly with {@link #carry(BiConsumer)}.</p>
 */
public final class LocaleScope implements AutoCloseable {

    private static final ThreadLocal<ECPluginData> CURRENT = new ThreadLocal<>();

    private final @Nullable ECPluginData previous;

    private LocaleScope(@Nullable ECPluginData previous) {
        this.previous = previous;
    }

    /**
     * Makes {@code plugin} the one running on this thread until the returned scope is closed (try-with-resources); a
     * null plugin leaves the current one in place. Scopes nest: closing restores the one before.
     */
    public static LocaleScope enter(@Nullable ECPluginData plugin) {
        ECPluginData previous = CURRENT.get();
        if (plugin != null) {
            CURRENT.set(plugin);
        }
        return new LocaleScope(previous);
    }

    /** The plugin running on this thread, null outside any scope. */
    public static @Nullable ECPluginData current() {
        return CURRENT.get();
    }

    /**
     * {@code reply} bound to the plugin running NOW: it runs inside that scope whatever thread completes the future -
     * {@code future.whenComplete(LocaleScope.carry((value, error) -> MESSAGE.send(player)))}.
     */
    public static <T> BiConsumer<T, Throwable> carry(BiConsumer<T, Throwable> reply) {
        ECPluginData running = CURRENT.get();
        return (value, error) -> {
            try (LocaleScope ignored = enter(running)) {
                reply.accept(value, error);
            }
        };
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
