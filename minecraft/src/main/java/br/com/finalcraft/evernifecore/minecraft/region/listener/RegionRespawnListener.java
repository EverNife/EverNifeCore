package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Sends a player who died inside a region with {@code spawn-point} to that point ({@link EventSource#RESPAWN}). The bed
 * or anchor is already in the event when it is raised, so setting the location here wins over it; a plugin listening
 * later still gets the last word.
 */
public final class RegionRespawnListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location died = player.getLocation();   //still where they died while the event is raised
        if (RegionContexts.isDisabled(died.getWorld())) {
            return;
        }
        WorldLocPos point = RegionLookup.get().resolve(CoreFlags.SPAWN_POINT, died.getWorld().getName(), died.getX(), died.getY(), died.getZ(),
                FCBukkitUtil.adapt(player));
        World world = point == null ? null : Bukkit.getWorld(point.getWorldName());
        if (world != null) {
            event.setRespawnLocation(RegionContexts.toLocation(point, new Location(world, 0, 0, 0)));
        }
    }
}
