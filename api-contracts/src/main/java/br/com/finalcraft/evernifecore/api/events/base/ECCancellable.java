package br.com.finalcraft.evernifecore.api.events.base;

/**
 * The cancellation contract of an EC event - the only way an event of this framework may be
 * cancelled. Each platform ships the real interface under this same name, extending its own
 * ({@code org.bukkit.event.Cancellable}, Hytale's {@code ICancellable}), so a native listener
 * cancels through the API it already knows and the bus sees it.
 */
public interface ECCancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);

}
