package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.stream.Collectors;

public class CMDItemInfo {

    @FinalCMD(
            aliases = {"iteminfo"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void onCommand(Player player) {

        ItemStack heldItem = FCBukkitUtil.getPlayersHeldItem(player);

        if (heldItem == null){
            FCMessageUtil.needsToBeHoldingItem(player);
            return;
        }

        player.sendMessage("§m§7" + FCTextUtil.straightLineOf("-"));
        if (NMSUtils.get() != null){
            String localizedName = FCItemUtils.getLocalizedName(heldItem);
            String uncolorfiedLocalizedName = FCColorUtil.decolorfy(localizedName);
            FancyText.of("§d ● §eLocalizedName: §a" + localizedName)
                    .setHoverText("§7Click to copy! [§a" + uncolorfiedLocalizedName + "§7]")
                    .setSuggestCommandAction(uncolorfiedLocalizedName)
                    .send(player);
        }

        String displayName = FCItemUtils.getDisplayName(heldItem);
        if (displayName != null){
            String uncolorfiedDisplayName = FCColorUtil.decolorfy(displayName);
            FancyText.of("§d ● §eDisplayName: §r" + displayName)
                    .setHoverText("§7Click to copy! [§a" + uncolorfiedDisplayName + "§7]")
                    .setSuggestCommandAction(uncolorfiedDisplayName)
                    .send(player);
        }

        if (NMSUtils.get() != null){
            FancyText.of("\n§d ● §eMCIdentifier: §a" + FCItemUtils.getMinecraftIdentifier(heldItem))
                    .setHoverText("§7Click to copy! [§a" + FCItemUtils.getMinecraftIdentifier(heldItem) + "§7]")
                    .setSuggestCommandAction(FCItemUtils.getMinecraftIdentifier(heldItem))
                    .send(player);
        }

        String readLines = ItemDataPart.readItem(heldItem).stream().collect(Collectors.joining("\n - "));

        FancyText.of("§b ▼ ")
                .setHoverText("§7Click to copy! [§a" + FCItemUtils.getBukkitIdentifier(heldItem) + "§7]")
                .setSuggestCommandAction(FCItemUtils.getBukkitIdentifier(heldItem))

                .append("\n - " + readLines)
                .setHoverText("§b - " + readLines)
                .setSuggestCommandAction(readLines)
                .send(player);

    }

}
