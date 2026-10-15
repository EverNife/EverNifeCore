package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;

/** {@link RegionTerrainListener} for a block exploding; its own class because {@link BlockExplodeEvent} arrived in 1.8. */
public final class RegionTerrainBlockExplodeListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        RegionTerrainListener.recordInWorld(event.blockList());
    }
}
