package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.Locale;
import java.util.Objects;

/**
 * Who runs a piece of logic: the plugin that owns the definition and a readable origin inside it
 * ({@code shop/vip#slot12/rule1}). Every audit line, trace line and error names it. Immutable.
 */
public final class Caller {

    private final ECPluginData plugin;
    private final String origin;
    private final String text;

    private Caller(ECPluginData plugin, String origin) {
        this.plugin = plugin;
        this.origin = origin;
        this.text = (plugin.getMetaInfo().getName() + ":" + origin).toLowerCase(Locale.ROOT);
    }

    /** @throws NullPointerException when the plugin or the origin is missing */
    public static Caller of(ECPluginData plugin, String origin) {
        Objects.requireNonNull(plugin, "A Caller names the plugin that owns the logic - 'plugin' cannot be null.");
        Objects.requireNonNull(origin, "A Caller names where the logic was written ('shop/vip#slot12') - 'origin' cannot be null.");
        return new Caller(plugin, origin);
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    public String getOrigin() {
        return origin;
    }

    /** {@code <plugin>:<origin>}, lowercase. */
    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Caller && ((Caller) other).plugin == plugin && ((Caller) other).origin.equals(origin);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }
}
