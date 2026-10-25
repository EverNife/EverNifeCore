package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.event.RespawnActionEvent;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
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
 * later still gets the last word. A respawn cannot be cancelled on Bukkit: only the {@link RespawnActionEvent} Post is
 * posted, asked where the player died.
 */
public final class RespawnActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location died = player.getLocation();   //still where they died while the event is raised
        if (McActionContexts.isDisabled(died.getWorld())) {
            return;
        }
        WorldLocPos point = RegionLookup.get().resolve(CoreFlags.SPAWN_POINT, died.getWorld().getName(), died.getX(), died.getY(), died.getZ(),
                FCBukkitUtil.adapt(player));
        World world = point == null ? null : Bukkit.getWorld(point.getWorldName());
        if (world != null) {
            event.setRespawnLocation(McActionContexts.toLocation(point, new Location(world, 0, 0, 0)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!McActionContexts.isDisabled(player.getWorld())) {
            Actions.post(RespawnActionEvent.Post.class, () -> new RespawnActionEvent.Post(new SimpleContext(EventSource.RESPAWN,
                    McActionContexts.position(player.getLocation()), McActionContexts.actor(player), event)));
        }
    }
}
