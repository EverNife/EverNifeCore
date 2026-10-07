package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Objects;

/** Hands the server's world load and unload to the {@link McPlatform} listeners - the region index follows them. */
public final class McWorldListener implements ECListener {

    private final McPlatform platform;

    public McWorldListener(McPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "'platform' cannot be null: the world hooks fire into it!");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        platform.fireWorldLoaded(event.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)   //a cancelled unload left the world where it was
    public void onWorldUnload(WorldUnloadEvent event) {
        platform.fireWorldUnloaded(event.getWorld().getName());
    }
}
