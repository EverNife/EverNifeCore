package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.event.FishActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * A catch reeled in ({@link EventSource#FISH}): only {@code CAUGHT_FISH} with an item on the hook, asked where the player
 * stands. A denial, or a cancelled {@code Pre}, leaves the catch in the water; one that went through posts its
 * {@link FishActionEvent} Post, whose item entity is the catch flying to the player.
 */
public final class FishActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Item caught = caught(event);
        if (caught != null && Actions.denied(context(event, caught), FishActionEvent.Pre.class, FishActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterFish(PlayerFishEvent event) {
        Item caught = caught(event);
        if (caught != null) {
            Actions.post(FishActionEvent.Post.class, () -> new FishActionEvent.Post(context(event, caught)));
        }
    }

    /** The item reeled in; null for any other state of the rod, or in a disabled world. */
    private static @Nullable Item caught(PlayerFishEvent event) {
        return event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item
                && !McActionContexts.isDisabled(event.getPlayer().getWorld()) ? (Item) event.getCaught() : null;
    }

    private static ItemContext context(PlayerFishEvent event, Item caught) {
        return new ItemContext(EventSource.FISH, McActionContexts.position(event.getPlayer().getLocation()),
                McActionContexts.actor(event.getPlayer()), event, McActionContexts.key(caught.getItemStack()),
                caught.getItemStack().getAmount(), caught.getUniqueId());
    }
}
