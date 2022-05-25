package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.version.MCVersion;
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

    @FCLocale(lang = LocaleType.EN_US, text = "§7§o[INFO] (%x%, %y%, %z%) §b%block_type% §a§l[%block_id_and_meta%] &7&o(%biome%)",
            hover = "§7Disable with /blockinfo\nClick to copy the Material Name"
    )
    private static LocaleMessage BLOCK_DEBUG;

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

                if (MCVersion.isBellow1_13()){
                    itemStack.setDurability(block.getData());
                }else {
                    itemStack.setData(block.getState().getData());
                }

                BLOCK_DEBUG
                        .addPlaceholder("%x%", location.getBlockX())
                        .addPlaceholder("%y%", location.getBlockY())
                        .addPlaceholder("%z%", location.getBlockZ())
                        .addPlaceholder("%block_type%", block.getType().name())
                        .addPlaceholder("%block_id_and_meta%", !MCVersion.isBellow1_13() ? "" : block.getType().getId())
                        .addPlaceholder("%biome%", block.getBiome().name())
                        .addSuggest(FCItemUtils.getBukkitIdentifier(itemStack))
                        .send(player);
            }
        }
    }

}
