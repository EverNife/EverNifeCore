package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiViews;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Where a layout comes from: {@code Layouts.of(LojaLayout.class)} reads the file the first time, caches
 * the result and hands the same instance out from then on.
 *
 * <p>Each layout loads on its own, when it is first asked for. There is no initialization order to get
 * right and no static field for anyone to read too early - which also means a layout that reads another
 * layout while loading would spin forever, so that is refused by name instead.</p>
 */
public final class Layouts {

    private static final Map<String, LayoutBase> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<Class<? extends LayoutBase>>> BY_PLUGIN = new ConcurrentHashMap<>();
    private static final ThreadLocal<LinkedHashSet<String>> RESOLVING =
            ThreadLocal.withInitial(LinkedHashSet::new);

    private Layouts() {
    }

    /** The layout as every viewer with no overlay of their own reads it. */
    @Nonnull
    public static <T extends LayoutBase> T of(@Nonnull Class<T> type) {
        return of(type, null);
    }

    /**
     * The layout as a viewer reading {@code language} sees it: the base file, with the keys of
     * {@code guis/locale/<language>/} on top when the admin wrote such an overlay.
     *
     * <p>Without an overlay there is nothing to differ, so every language shares the base copy.</p>
     */
    @Nonnull
    public static <T extends LayoutBase> T of(@Nonnull Class<T> type, @Nullable String language) {
        ECPluginData plugin = ECPluginManager.getProvidingPlugin(type);
        String lang = language == null ? null : LocaleType.normalize(language);
        if (lang != null && LayoutScanner.openOverlay(plugin, type, lang) == null) {
            lang = null;
        }

        String key = keyOf(type, lang);
        LayoutBase cached = CACHE.get(key);
        if (cached != null) {
            return type.cast(cached);
        }

        LinkedHashSet<String> resolving = RESOLVING.get();
        if (!resolving.add(key)) {
            throw new IllegalStateException("The layouts " + String.join(" -> ", resolving) + " -> " + key
                    + " read each other while loading, so neither can finish. Share icons through "
                    + "inheritance - put the common ones in a base layout both extend - instead of "
                    + "reading one layout's field from the other.");
        }
        try {
            T loaded = LayoutScanner.load(plugin, type, lang);
            CACHE.put(key, loaded);
            registeredOf(plugin).add(type);
            return loaded;
        } finally {
            resolving.remove(key);
            if (resolving.isEmpty()) {
                RESOLVING.remove();
            }
        }
    }

    /**
     * The layout, but only where {@code condition} holds - a screen that depends on something this
     * server may not have (a mod's registry, another plugin) is absent rather than broken.
     *
     * <p>Empty is an answer the caller has to handle; it is never a null that fails later, far from
     * whatever made it null.</p>
     */
    @Nonnull
    public static <T extends LayoutBase> Optional<T> ifAvailable(@Nonnull Class<T> type,
                                                                 @Nonnull BooleanSupplier condition) {
        return condition.getAsBoolean() ? Optional.of(of(type)) : Optional.<T>empty();
    }

    /**
     * Drops every language of {@code type} from the cache, reads the file again and re-renders the
     * screens that are open, so nobody has to be kicked out of a menu to see a change.
     *
     * @return how many open screens were re-rendered
     */
    public static int reload(@Nonnull Class<? extends LayoutBase> type) {
        forget(type);
        of(type);
        return GuiViews.refreshAll();
    }

    /**
     * The same as {@link #reload(Class)} for every layout of {@code plugin} that has been loaded.
     *
     * @return how many open screens were re-rendered
     */
    public static int reloadAll(@Nonnull ECPluginData plugin) {
        for (Class<? extends LayoutBase> type : new ArrayList<>(registeredOf(plugin))) {
            forget(type);
            of(type);
        }
        return GuiViews.refreshAll();
    }

    /** Every layout of {@code plugin} that has been asked for at least once. */
    @Nonnull
    public static Set<Class<? extends LayoutBase>> getRegistered(@Nonnull ECPluginData plugin) {
        return Collections.unmodifiableSet(registeredOf(plugin));
    }

    /** The registered layout whose simple class name is {@code layoutName}, case-insensitively. */
    @Nonnull
    public static Optional<Class<? extends LayoutBase>> findRegistered(@Nonnull ECPluginData plugin,
                                                                       @Nonnull String layoutName) {
        for (Class<? extends LayoutBase> type : registeredOf(plugin)) {
            if (type.getSimpleName().equalsIgnoreCase(layoutName)) {
                return Optional.<Class<? extends LayoutBase>>of(type);
            }
        }
        return Optional.empty();
    }

    /** Forgets every cached copy of {@code type}, in every language. */
    public static void forget(@Nonnull Class<? extends LayoutBase> type) {
        String prefix = type.getName() + '#';
        List<String> keys = new ArrayList<>(CACHE.keySet());
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                CACHE.remove(key);
            }
        }
    }

    /** Forgets everything. Meant for a test that must not read another test's file. */
    public static void clear() {
        CACHE.clear();
        BY_PLUGIN.clear();
    }

    private static String keyOf(Class<?> type, String language) {
        return type.getName() + '#' + (language == null ? "" : language);
    }

    private static Set<Class<? extends LayoutBase>> registeredOf(ECPluginData plugin) {
        String name = plugin.getMetaInfo().getName();
        Set<Class<? extends LayoutBase>> registered = BY_PLUGIN.get(name);
        if (registered == null) {
            registered = Collections.newSetFromMap(
                    new ConcurrentHashMap<Class<? extends LayoutBase>, Boolean>());
            Set<Class<? extends LayoutBase>> raced = BY_PLUGIN.putIfAbsent(name, registered);
            if (raced != null) {
                registered = raced;
            }
        }
        return registered;
    }

}
