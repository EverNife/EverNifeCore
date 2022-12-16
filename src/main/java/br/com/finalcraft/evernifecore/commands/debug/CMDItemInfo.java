package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.material.FCMaterialUtil;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
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

        if (NMSUtils.get() != null){
            FancyText.of("§d ● §eLocalizedName: §a" + FCItemUtils.getLocalizedName(heldItem))
                    .setHoverText("Click to copy!")
                    .setSuggestCommandAction(FCItemUtils.getLocalizedName(heldItem))
                    .send(player);

            FancyText.of("§d ● §eMCIdentifier: §a" + FCItemUtils.getMinecraftIdentifier(heldItem))
                    .setHoverText("Click to copy!")
                    .setSuggestCommandAction(FCItemUtils.getMinecraftIdentifier(heldItem))
                    .send(player);
        }

        String readLines = "§a" + ItemDataPart.readItem(heldItem).stream().collect(Collectors.joining("\n"));
        FancyText.of(readLines)
                .setHoverText(readLines)
                .setSuggestCommandAction(readLines)
                .send(player);

    }

}
