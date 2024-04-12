package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDEntityInfo;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractListener implements ECListener {

    @FCLocale(lang = LocaleType.EN_US,
            text = "§7§o[INFO] (%x%, %y%, %z%) §b%block_type%%block_meta% §a§l[%block_id%%block_meta%] &7&o(%biome%)",
            hover = "§7Disable with /blockinfo" +
                    "\nClick to copy the Material Name"
    )
    private static LocaleMessage BLOCK_DEBUG;

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onRightClick(PlayerInteractEvent event) {

        if (event.getAction() == Action.PHYSICAL) {
            return;
        }

        final Player player = event.getPlayer();

        Block block = event.getClickedBlock();

        if (block == null || CMDBlockInfo.isInDebugMode(player) == false){
            return;
        }

        Location location = block.getLocation();

        if (player.isSneaking()){
            //Get faced block then
            BlockFace face = event.getBlockFace();
            block = block.getRelative(face);
            location = block.getLocation();
        }

        ItemStack itemStack = new ItemStack(block.getType());

        if (MCVersion.isLowerEquals(MCVersion.v1_12)){
            itemStack.setDurability(block.getData());
        }else {
            itemStack.setData(block.getState().getData());
        }

        BLOCK_DEBUG
                .addPlaceholder("%x%", location.getBlockX())
                .addPlaceholder("%y%", location.getBlockY())
                .addPlaceholder("%z%", location.getBlockZ())
                .addPlaceholder("%block_type%", block.getType().name())
                .addPlaceholder("%block_id%", MCVersion.isHigherEquals(MCVersion.v1_13) ? "" : block.getType().getId())
                .addPlaceholder("%block_meta%", MCVersion.isHigherEquals(MCVersion.v1_13) ? "" : block.getData() == 0 ? "" : ":" + block.getData())
                .addPlaceholder("%biome%", block.getBiome().name())
                .addSuggest(FCItemUtils.getBukkitIdentifier(itemStack))
                .send(player);
    }

    @FCLocale(lang = LocaleType.EN_US,
            text = "§7§o[INFO] (%x%, %y%, %z%) §b%entity_type% &7&o(%entity_name%)" +
                    "\n - §7§oEntity ToString: §b%entity_tostring%",
            hover = "§7Disable with /entityinfo" +
                    "\nClick to copy the Entity Type",
            runCommand = "%entity_type%",
            clickActionType = ClickActionType.SUGGEST_COMMAND,

            children = {
                    @FCLocale.Child(
                            text = "\n - §7§oEntity NBT: §b%entity_nbt%",
                            hover = "§7Disable with /entityinfo" +
                                    "\nClick to copy the Entity NBT",
                            runCommand = "%entity_nbt%",
                            clickActionType = ClickActionType.SUGGEST_COMMAND
                    )
            }
    )
    private static LocaleMessage ENTITY_INFO;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {

        Entity rightClicked = event.getRightClicked();

        if (rightClicked == null || CMDEntityInfo.isInDebugMode(event.getPlayer()) == false){
            return;
        }

        Player player = event.getPlayer();

        boolean includeNBT = player.isSneaking();

        Location location = rightClicked.getLocation();

        String entityName = rightClicked.getName();
        try {
            if (rightClicked.getCustomName() != null) entityName = rightClicked.getCustomName();
        }catch (NoSuchMethodError ignored){

        }

        ENTITY_INFO
                .addPlaceholder("%x%", location.getBlockX())
                .addPlaceholder("%y%", location.getBlockY())
                .addPlaceholder("%z%", location.getBlockZ())
                .addPlaceholder("%entity_type%", rightClicked.getType().name())
                .addPlaceholder("%entity_name%", entityName)
                .addPlaceholder("%entity_tostring%", NMSUtils.get().asMinecraftEntity(rightClicked).toString())
                .addPlaceholder("%entity_nbt%", includeNBT ? FCNBTUtil.getFrom(rightClicked).toString() : "(Hold Shift to Scan)")
                .send(player);

    }

}
