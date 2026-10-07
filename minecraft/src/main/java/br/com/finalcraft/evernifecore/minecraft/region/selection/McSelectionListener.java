package br.com.finalcraft.evernifecore.minecraft.region.selection;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.selection.SelectionClick;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;

/**
 * Turns a block clicked with the selection tool into a {@link SelectionTool#handleClick} and cancels what was a
 * selection, so the tool never breaks, tills or places. It runs before the region flags do, which then skip the
 * cancelled click: a {@code deny-interact} does not keep a player from selecting inside the region.
 */
public final class McSelectionListener implements ECListener {

    private final McSelectionItems items;

    public McSelectionListener(McSelectionItems items) {
        this.items = Objects.requireNonNull(items, "'items' cannot be null: they tell a selection tool from any other item!");
    }

    @Override
    public boolean silentRegistration() {
        return true;   //comes and goes with the open selections
    }

    /**
     * From 1.9 a click arrives once per hand, and only the event of the hand that holds the tool carries it as its
     * item - one click, one point, without ever asking which hand.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !items.isSelectionTool(event.getItem())) {
            return;
        }
        SelectionClick click;
        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK:
                click = SelectionClick.LEFT;
                break;
            case RIGHT_CLICK_BLOCK:
                click = event.getPlayer().isSneaking() ? SelectionClick.SNEAK_RIGHT : SelectionClick.RIGHT;
                break;
            default:
                return;
        }
        WorldBlockPos at = WorldBlockPos.of(block.getX(), block.getY(), block.getZ(), block.getWorld().getName());
        if (SelectionTool.handleClick(FCBukkitUtil.adapt(event.getPlayer()), at, click)) {
            event.setCancelled(true);
        }
    }
}
