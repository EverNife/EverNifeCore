package br.com.finalcraft.evernifecore.api.events.base;

import org.bukkit.event.Cancellable;

/** The Bukkit face of the EC cancellation contract: a native listener cancels through Bukkit's own. */
public interface ECCancellable extends Cancellable {

}
