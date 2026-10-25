package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.ExploreContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ExploreActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * A player stepping into another chunk ({@link EventSource#EXPLORE}), asked where he arrives. A denial, or a cancelled
 * {@code Pre}, cancels the step; one that went through posts its {@link ExploreActionEvent} Post. Most moves stay inside
 * one chunk and leave at the arithmetic, before anything is built.
 */
public final class ExploreActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        ExploreContext context = context(event);
        if (context != null && Actions.denied(context, ExploreActionEvent.Pre.class, ExploreActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterMove(PlayerMoveEvent event) {
        if (crossesChunk(event)) {
            Actions.post(ExploreActionEvent.Post.class, () -> new ExploreActionEvent.Post(context(event)));
        }
    }

    //chunk coordinates are arithmetic on the location; getChunk() would be a chunk-map lookup
    private static boolean crossesChunk(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return to != null && !ChunkPos.fromBlock(from.getBlockX(), from.getBlockZ()).equals(ChunkPos.fromBlock(to.getBlockX(), to.getBlockZ()))
                && !McActionContexts.isDisabled(to.getWorld());
    }

    /** Null for a move inside one chunk, or in a disabled world. */
    private static @Nullable ExploreContext context(PlayerMoveEvent event) {
        if (!crossesChunk(event)) {
            return null;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        return new ExploreContext(McActionContexts.position(to), McActionContexts.actor(event.getPlayer()), event,
                ChunkPos.fromBlock(from.getBlockX(), from.getBlockZ()), ChunkPos.fromBlock(to.getBlockX(), to.getBlockZ()));
    }
}
