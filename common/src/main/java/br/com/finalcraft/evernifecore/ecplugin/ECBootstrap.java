package br.com.finalcraft.evernifecore.ecplugin;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The live bootstrap instance of one plugin, published by the core and read back as a field.
 *
 * <p>A plugin's common bootstrap declares one and reads it through its own accessor - the whole of
 * what publishing an instance costs:</p>
 *
 * <pre>{@code
 * public interface TemplateBootstrap extends IECPluginBootstrap {
 *
 *     ECBootstrap<TemplateBootstrap> INSTANCE = ECBootstrap.of(TemplateBootstrap.class);
 *
 *     static TemplateBootstrap get() {
 *         return INSTANCE.get();
 *     }
 * }
 * }</pre>
 *
 * <p>The core fills it when the platform builds the plugin ({@link IECPluginBootstrap#runECPluginInstantiate()})
 * and again on every enable, and empties it once the shutdown has finished - so a holder answers
 * {@code null} exactly while there is no plugin to hand out, and an outgoing instance never
 * unpublishes the one that replaced it.</p>
 *
 * <p>A holder takes whatever is an instance of its type, so a platform main class may declare one of
 * its own beside the shared one; both then hold the same object.</p>
 */
public final class ECBootstrap<T extends IECPluginBootstrap> {

    private static final List<ECBootstrap<?>> HOLDERS = new ArrayList<>();
    private static final List<IECPluginBootstrap> PUBLISHED = new ArrayList<>();

    private final Class<T> type;
    private volatile T instance;

    private ECBootstrap(Class<T> type) {
        this.type = type;
    }

    /** A holder for {@code bootstrapType}, already filled if such a plugin is running. */
    public static synchronized <T extends IECPluginBootstrap> ECBootstrap<T> of(Class<T> bootstrapType) {
        ECBootstrap<T> holder = new ECBootstrap<>(bootstrapType);
        HOLDERS.add(holder);

        //a holder may be built after its plugin was published: an interface is initialized when
        //something first reads it, which can be the very first call to the plugin's accessor
        for (IECPluginBootstrap published : PUBLISHED) {
            holder.takeIfMine(published);
        }
        return holder;
    }

    /** The plugin this holder is for, or {@code null} while there is none. */
    @Nullable
    public T get() {
        return instance;
    }

    @Override
    public String toString() {
        return "ECBootstrap(" + type.getName() + " -> " + instance + ")";
    }

    // ------------------------------------------------------------------
    //  Framework side - driven by IECPluginBootstrap's orchestration
    // ------------------------------------------------------------------

    static synchronized void publish(IECPluginBootstrap plugin) {
        if (!isPublished(plugin)) {
            PUBLISHED.add(plugin);
        }
        for (ECBootstrap<?> holder : HOLDERS) {
            holder.takeIfMine(plugin);
        }
    }

    static synchronized void unpublish(IECPluginBootstrap plugin) {
        forget(plugin);
        for (ECBootstrap<?> holder : HOLDERS) {
            holder.releaseIfMine(plugin);
        }
    }

    /** Empties every holder. The test engine's reset - production never takes an instance back this way. */
    static synchronized void forgetAll() {
        PUBLISHED.clear();
        for (ECBootstrap<?> holder : HOLDERS) {
            holder.instance = null;
        }
    }

    private void takeIfMine(IECPluginBootstrap plugin) {
        if (type.isInstance(plugin)) {
            this.instance = type.cast(plugin);
        }
    }

    private void releaseIfMine(IECPluginBootstrap plugin) {
        //by identity: a plugin re-instantiated at runtime leaves the outgoing object shutting down
        //after the new one already published itself, and only its own publication is its to take back
        if (this.instance == plugin) {
            this.instance = null;
        }
    }

    private static boolean isPublished(IECPluginBootstrap plugin) {
        for (IECPluginBootstrap published : PUBLISHED) {
            if (published == plugin) {
                return true;
            }
        }
        return false;
    }

    private static void forget(IECPluginBootstrap plugin) {
        for (int i = PUBLISHED.size() - 1; i >= 0; i--) {
            if (PUBLISHED.get(i) == plugin) {
                PUBLISHED.remove(i);
            }
        }
    }
}
