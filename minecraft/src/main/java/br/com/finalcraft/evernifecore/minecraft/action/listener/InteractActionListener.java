package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.InteractActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import jakarta.annotation.Nullable;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Asks the regions about every block and entity a player uses; a denial, or a cancelled {@code Pre}, cancels it, and a
 * use that went through posts its {@link InteractActionEvent} Post. The listener only builds the context - a
 * {@link BlockContext} or an {@link EntityContext} - and which flag speaks for it is the core's call.
 */
public final class InteractActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    /**
     * A click in the air never gets here: Bukkit reports it cancelled. From 1.9 a click arrives once per hand, and a
     * denial simply repeats - the hand is never read, since the hand accessor does not exist below 1.9.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        decide(event, context(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterInteract(PlayerInteractEvent event) {
        if (isAsked(event)) {
            Actions.post(InteractActionEvent.Post.class, () -> new InteractActionEvent.Post(context(event)));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        decide(event, context(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterInteractEntity(PlayerInteractEntityEvent event) {
        if (!McActionContexts.isDisabled(event.getRightClicked().getWorld())) {
            Actions.post(InteractActionEvent.Post.class, () -> new InteractActionEvent.Post(context(event)));
        }
    }

    private static void decide(Cancellable event, @Nullable ActionContext context) {
        if (context != null && Actions.denied(context, InteractActionEvent.Pre.class, InteractActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    private static boolean isAsked(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        return block != null && !McActionContexts.isDisabled(block.getWorld());
    }

    private static @Nullable BlockContext context(PlayerInteractEvent event) {
        if (!isAsked(event)) {
            return null;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        return new BlockContext(EventSource.INTERACT, McActionContexts.position(block.getLocation()),
                McActionContexts.actor(player, FCBukkitUtil.isFakePlayer(player)), event, McActionContexts.key(block.getType()),
                McActionContexts.itemInHandKey(player));
    }

    private static @Nullable EntityContext context(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (McActionContexts.isDisabled(entity.getWorld())) {
            return null;
        }
        return new EntityContext(EventSource.INTERACT, McActionContexts.position(entity.getLocation()), McActionContexts.actor(event.getPlayer()), event,
                McActionContexts.actor(entity));
    }
}
