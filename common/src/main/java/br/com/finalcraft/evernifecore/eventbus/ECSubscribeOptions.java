package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * How a subscription wants to be delivered to: its priority, whether it steps aside for a cancelled
 * event, whether it hears the class it named only or its subtypes too, and which plugin owns it.
 * Immutable: every {@code withX} hands back a copy, and {@link #defaults()} is one shared instance.
 *
 * <pre>{@code
 * bus.subscribe(ShopPurchaseEvent.class,
 *         ECSubscribeOptions.ownedBy(getPluginData()).withPriority(ECEventPriority.LATE).withIgnoreCancelled(true),
 *         event -> ledger.record(event));
 * }</pre>
 */
public final class ECSubscribeOptions {

    private static final ECSubscribeOptions DEFAULTS =
            new ECSubscribeOptions(ECEventPriority.NORMAL.getValue(), false, false, null);

    private final short priority;
    private final boolean ignoreCancelled;
    private final boolean exact;
    private final ECPluginData plugin;

    private ECSubscribeOptions(short priority, boolean ignoreCancelled, boolean exact, ECPluginData plugin) {
        this.priority = priority;
        this.ignoreCancelled = ignoreCancelled;
        this.exact = exact;
        this.plugin = plugin;
    }

    /** {@link ECEventPriority#NORMAL}, delivered even when cancelled, subtypes included, owned by nobody. */
    public static ECSubscribeOptions defaults() {
        return DEFAULTS;
    }

    /** {@link #defaults()} owned by {@code plugin}: dropped with the rest of that plugin's on its shutdown. */
    public static ECSubscribeOptions ownedBy(ECPluginData plugin) {
        Objects.requireNonNull(plugin, "'plugin' cannot be null when building owned ECSubscribeOptions: use defaults() for a subscription nobody owns.");
        return DEFAULTS.withPlugin(plugin);
    }

    /** The options an {@link ECEventHandler} asked for - the one place the annotation is read into the bus. */
    static ECSubscribeOptions of(ECEventHandler annotation, @Nullable ECPluginData plugin) {
        return create(ECEventPriority.of(annotation), annotation.ignoreCancelled(), annotation.exact(), plugin);
    }

    public ECSubscribeOptions withPriority(ECEventPriority priority) {
        Objects.requireNonNull(priority, "'priority' cannot be null on ECSubscribeOptions.");
        return withPriority(priority.getValue());
    }

    /** A priority between the {@link ECEventPriority} steps, for the subscriber that has to sit right before or after another one. */
    public ECSubscribeOptions withPriority(short priority) {
        return priority == this.priority ? this : create(priority, ignoreCancelled, exact, plugin);
    }

    /** Whether to skip the delivery once the event - an {@code ECCancellable} - is cancelled. */
    public ECSubscribeOptions withIgnoreCancelled(boolean ignoreCancelled) {
        return ignoreCancelled == this.ignoreCancelled ? this : create(priority, ignoreCancelled, exact, plugin);
    }

    /** Whether to hear the named class only: with {@code true} no subtype of it reaches the subscriber. */
    public ECSubscribeOptions withExact(boolean exact) {
        return exact == this.exact ? this : create(priority, ignoreCancelled, exact, plugin);
    }

    /** The owning plugin, or {@code null} for a subscription nobody drains. */
    public ECSubscribeOptions withPlugin(@Nullable ECPluginData plugin) {
        return plugin == this.plugin ? this : create(priority, ignoreCancelled, exact, plugin);
    }

    //a wither that changes nothing hands back the instance it was called on, and the values everybody
    //uses share one instance - so a plain subscribe allocates nothing here
    private static ECSubscribeOptions create(short priority, boolean ignoreCancelled, boolean exact, ECPluginData plugin) {
        if (priority == DEFAULTS.priority && !ignoreCancelled && !exact && plugin == null) {
            return DEFAULTS;
        }
        return new ECSubscribeOptions(priority, ignoreCancelled, exact, plugin);
    }

    public short getPriority() {
        return priority;
    }

    public boolean isIgnoringCancelled() {
        return ignoreCancelled;
    }

    public boolean isExact() {
        return exact;
    }

    @Nullable
    public ECPluginData getPlugin() {
        return plugin;
    }

    @Nullable
    String getPluginName() {
        return plugin == null ? null : plugin.getMetaInfo().getName();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ECSubscribeOptions)) return false;
        ECSubscribeOptions that = (ECSubscribeOptions) other;
        return priority == that.priority
                && ignoreCancelled == that.ignoreCancelled
                && exact == that.exact
                && Objects.equals(getPluginName(), that.getPluginName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, ignoreCancelled, exact, getPluginName());
    }

    /** {@code "LATE, plugin=Shop, exact, ignoreCancelled"} - the priority first, then the plugin, then only the flags that are set. */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(describePriority(priority));
        if (plugin != null) {
            text.append(", plugin=").append(getPluginName());
        }
        if (exact) {
            text.append(", exact");
        }
        if (ignoreCancelled) {
            text.append(", ignoreCancelled");
        }
        return text.toString();
    }

    /** The step name when the value sits on one, the raw value otherwise. */
    static String describePriority(short priority) {
        for (ECEventPriority step : ECEventPriority.values()) {
            if (step.getValue() == priority) {
                return step.name();
            }
        }
        return String.valueOf(priority);
    }

}
