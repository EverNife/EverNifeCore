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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Feeds the {@code track-changes} journals ({@link RegionTerrain}) with what really happened: at {@code MONITOR}, after
 * every plugin had its say, and only for events nobody cancelled. A break or an explosion still has the old block in
 * the world here; a place has already set the new one, so its original is what the player's right click found there
 * before the place, or else the event's replaced state. An armor stand
 * or a hanging entity (frame, painting) broken is the entity removal kept; a mob dying is not.
 */
public final class RegionTerrainListener implements ECListener {

    private final BiFunction<BlockState, TerrainContent, String> replacedState;
    /** Per player, the blocks his last right click on a block could place over, read before the server placed it. */
    private final Map<UUID, BlockState[]> placeCandidates = new HashMap<>();   //main thread only

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

    //ponytail: two getState() per right click on a block while any region needs PLACE, not only over tracked positions
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || event.getItem() == null) {
            placeCandidates.remove(event.getPlayer().getUniqueId());
            return;
        }
        //a replaceable block (grass, snow) is placed over itself, anything else gets the block on the clicked face
        placeCandidates.put(event.getPlayer().getUniqueId(),
                new BlockState[]{clicked.getState(), clicked.getRelative(event.getBlockFace()).getState()});
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        BlockState[] candidates = placeCandidates.remove(event.getPlayer().getUniqueId());
        if (RegionContexts.isDisabled(block.getWorld())) return;
        //Crucible and Arclight build getBlockReplacedState() from the world after the place: it reports the new block
        BlockState clickedBefore = candidateAt(candidates, block);
        BlockState original = clickedBefore != null ? clickedBefore : event.getBlockReplacedState();
        RegionTerrain.recordChange(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                content -> replacedState.apply(original, content));
    }

    private static BlockState candidateAt(BlockState[] candidates, Block block) {
        if (candidates == null) return null;
        for (BlockState state : candidates) {
            if (state.getX() == block.getX() && state.getY() == block.getY() && state.getZ() == block.getZ()
                    && state.getWorld().getName().equals(block.getWorld().getName())) {
                return state;
            }
        }
        return null;
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
