package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractListener implements ECListener {

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onRightClick(PlayerInteractEvent event) {

        if (event.getAction() == Action.PHYSICAL) {
            return;
        }

        final Player player = event.getPlayer();

        Block block = event.getClickedBlock();
        if (block != null){
            if (CMDBlockInfo.isInDebugMode(player)){
                Location location = block.getLocation();
                if (player.isSneaking()){
                    //Get faced block then
                    BlockFace face = event.getBlockFace();
                    block = block.getRelative(face);
                    location = block.getLocation();
                }

                ItemStack itemStack = new ItemStack(block.getType());
                itemStack.setDurability(block.getData());

                FancyText.of("§7§o[INFO] (" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ") §b" + block.getType().name() + " §a§l[" + block.getType().getId() + ":" + block.getData() + "]")
                        .setHoverText("§7Disable with /blockinfo")
                        .setSuggestCommandAction(FCItemUtils.getBukkitIdentifier(itemStack))
                        .send(player);
            }
        }
    }

}
