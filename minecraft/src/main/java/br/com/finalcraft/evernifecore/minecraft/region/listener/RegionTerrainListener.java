package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.region.terrain.McTerrainAccess;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Feeds the {@code track-changes} journals ({@link RegionTerrain}) with what really happened: at {@code MONITOR}, after
 * every plugin had its say, and only for events nobody cancelled. A break or an explosion still has the old block in
 * the world here; a place has already set the new one, so its original comes from the replaced state. An armor stand
 * or a hanging entity (frame, painting) broken is the entity removal kept; a mob dying is not.
 */
public final class RegionTerrainListener implements ECListener {

    private final BiFunction<BlockState, TerrainContent, String> replacedState;

    public RegionTerrainListener() {
        this(McTerrainAccess::capture);
    }

    RegionTerrainListener(BiFunction<BlockState, TerrainContent, String> replacedState) {
        this.replacedState = replacedState;
    }

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        recordInWorld(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (RegionContexts.isDisabled(block.getWorld())) return;
        BlockState replaced = event.getBlockReplacedState();
        RegionTerrain.recordChange(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                content -> replacedState.apply(replaced, content));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        recordInWorld(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        recordRemoval(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity().getType().name().equals("ARMOR_STAND")) {   //by name: the type arrived in 1.8
            recordRemoval(event.getEntity());
        }
    }

    static void recordInWorld(List<Block> blocks) {
        for (Block block : blocks) {
            recordInWorld(block);
        }
    }

    private static void recordInWorld(Block block) {
        if (RegionContexts.isDisabled(block.getWorld())) return;
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        RegionTerrain.recordChange(world, x, y, z, content -> EverNifeCore.getPlatform().getTerrainAccess().captureBlock(world, x, y, z, content));
    }

    private static void recordRemoval(Entity entity) {
        Location at = entity.getLocation();
        if (RegionContexts.isDisabled(at.getWorld())) return;
        RegionTerrain.recordEntityRemoval(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), content -> McTerrainAccess.capture(entity));
    }
}
