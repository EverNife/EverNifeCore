package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.logic.Caller;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Locale;
import java.util.Objects;

/**
 * Who a pending {@link Delivery} comes from and why: the plugin ({@code finalquests}), the place inside it that
 * emitted it ({@code finalquests:mine-iron}) and a reason a player reads ({@code Quest reward}). Immutable.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DeliveryOrigin {

    private String plugin;
    private String emitter;
    private String reason;

    private DeliveryOrigin() {
        //Jackson no-arg constructor
    }

    private DeliveryOrigin(String plugin, String emitter, String reason) {
        this.plugin = plugin;
        this.emitter = emitter;
        this.reason = reason;
    }

    /** @param plugin the plugin's name, kept lower-cased */
    public static DeliveryOrigin of(String plugin, String emitter, String reason) {
        return new DeliveryOrigin(Objects.requireNonNull(plugin, "plugin").toLowerCase(Locale.ROOT),
                Objects.requireNonNull(emitter, "emitter"), reason == null ? "" : reason);
    }

    /** The caller's plugin and origin, with {@code reason}. */
    public static DeliveryOrigin of(Caller caller, String reason) {
        return of(caller.getPlugin().getMetaInfo().getName(), caller.getOrigin(), reason);
    }

    /** The plugin's name, lower-cased. */
    public String getPlugin() {
        return plugin;
    }

    public String getEmitter() {
        return emitter;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DeliveryOrigin)) {
            return false;
        }
        DeliveryOrigin that = (DeliveryOrigin) other;
        return plugin.equals(that.plugin) && emitter.equals(that.emitter) && reason.equals(that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, emitter, reason);
    }

    @Override
    public String toString() {
        return plugin + (emitter.isEmpty() ? "" : " (" + emitter + ")") + (reason.isEmpty() ? "" : ": " + reason);
    }
}
