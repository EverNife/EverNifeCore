package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.function.Predicate;

/**
 * Asks the regions about every block broken ({@link EventSource#BREAK}) or placed ({@link EventSource#PLACE}) - one
 * instance per source, so each comes and goes with its own need; a denial cancels the event.
 */
public final class RegionBlockListener implements ECListener {

    private final EventSource source;
    private final Predicate<Player> fakePlayers;

    public RegionBlockListener(EventSource source) {
        this(source, FCBukkitUtil::isFakePlayer);
    }

    RegionBlockListener(EventSource source, Predicate<Player> fakePlayers) {
        if (source != EventSource.BREAK && source != EventSource.PLACE) {
            throw new IllegalArgumentException("A RegionBlockListener hears BREAK or PLACE, not " + source + ": pick the one this instance follows.");
        }
        this.source = source;
        this.fakePlayers = fakePlayers;
    }

    @Override
    public boolean silentRegistration() {
        return true;   //comes and goes with the regions that need it; logging each turn would be noise
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (source == EventSource.BREAK) {   //both instances carry both handlers; only the one following BREAK decides
            decide(EventSource.BREAK, event.getBlock(), event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (source == EventSource.PLACE) {
            decide(EventSource.PLACE, event.getBlock(), event.getPlayer(), event);
        }
    }

    private void decide(EventSource source, Block block, Player player, Cancellable event) {
        if (RegionContexts.isDisabled(block.getWorld())) {
            return;
        }
        BlockContext context = new BlockContext(source, RegionContexts.position(block.getLocation()), RegionContexts.actor(player, fakePlayers.test(player)),
                event, RegionContexts.key(block.getType()), RegionContexts.itemInHandKey(player));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
