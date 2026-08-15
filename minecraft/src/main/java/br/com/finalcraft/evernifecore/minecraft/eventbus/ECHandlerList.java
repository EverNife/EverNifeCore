package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

/**
 * The {@link HandlerList} behind every EC event on Bukkit ({@link ECEvent#getHandlerListOf(Class)}):
 * a plain Bukkit list that also tells the bus whenever its listeners change, which is how a
 * {@link ECEventBus#watchListeners listener watch} follows a native registration the bus could not
 * see by itself.
 *
 * <p>Every mutation reports, not only the empty/non-empty flip: the bus already fires nothing unless a
 * watch actually changed state, and the pass it runs is a handful of map reads. The report runs after
 * the inherited method returned - outside its monitor - so a watch callback that registers or
 * unregisters a Bukkit listener of its own can never wait on this list.</p>
 *
 * <p>Bukkit's {@code registerAll} and the static {@code unregisterAll(Plugin)} /
 * {@code unregisterAll(Listener)} reach the four overrides below. The static {@code unregisterAll()}
 * does not - it clears every list directly, and only runs when the server itself goes down.</p>
 */
public class ECHandlerList extends HandlerList {

    @Override
    public void register(RegisteredListener listener) {
        super.register(listener);
        ECEventBus.global().refreshListenerWatches();
    }

    @Override
    public void unregister(RegisteredListener listener) {
        super.unregister(listener);
        ECEventBus.global().refreshListenerWatches();
    }

    @Override
    public void unregister(Plugin plugin) {
        super.unregister(plugin);
        ECEventBus.global().refreshListenerWatches();
    }

    @Override
    public void unregister(Listener listener) {
        super.unregister(listener);
        ECEventBus.global().refreshListenerWatches();
    }

}
