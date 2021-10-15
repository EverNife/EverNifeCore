package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListenerGlobal implements ECListener {

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onRightClick(PlayerInteractEvent event) {

        if (event.getAction() == Action.PHYSICAL) {
            return;
        }

        final Player player = event.getPlayer();

        final Block block = event.getClickedBlock();
        if (block != null){
            if (CMDBlockInfo.isInDebugMode(player)){
                Location location = player.getLocation();

                ItemStack itemStack = new ItemStack(block.getType());
                itemStack.setDurability(block.getData());

                FancyText.of("§7§o[INFO] (" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ") §b" + block.getType().name() + " §a§l[" + block.getType().getId() + ":" + block.getData() + "]")
                        .setHoverText("§7Disable with /blockinfo")
                        .setSuggestCommandAction(FCItemUtils.getBukkitIdentifier(itemStack))
                        .send(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED){
            return;
        }

        UUIDsController.addUUIDName(event.getUniqueId(),event.getName());
        PlayerController.getOrCreateOne(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        PlayerData playerData = PlayerController.getPlayerData(event.getPlayer());
        playerData.setPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        PlayerData playerData = PlayerController.getPlayerData(event.getPlayer());
        playerData.setPlayer(null);
    }

}
