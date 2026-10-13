package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.EntityContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Asks the regions about every block and entity a player uses; a denial cancels it. The listener only builds the
 * context - a {@link BlockContext} or an {@link EntityContext} - and which flag speaks for it is the core's call.
 */
public final class RegionInteractListener implements ECListener {

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
        Block block = event.getClickedBlock();
        if (block == null || RegionEvents.isDisabled(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        BlockContext context = new BlockContext(EventSource.INTERACT, RegionEvents.position(block.getLocation()), FCBukkitUtil.adapt(player),
                event, RegionEvents.id(block.getType()), RegionEvents.itemInHandId(player), FCBukkitUtil.isFakePlayer(player));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (RegionEvents.isDisabled(entity.getWorld())) {
            return;
        }
        EntityContext context = new EntityContext(RegionEvents.position(entity.getLocation()), FCBukkitUtil.adapt(event.getPlayer()), event,
                RegionEvents.id(entity.getType()), entity instanceof Player ? FCBukkitUtil.adapt((Player) entity) : null);
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
