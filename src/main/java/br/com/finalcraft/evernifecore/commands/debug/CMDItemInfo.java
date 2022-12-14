package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.material.FCMaterialUtil;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
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

        String readLines = ItemDataPart.readItem(heldItem).stream().collect(Collectors.joining("\n"));
        FancyText.of(readLines)
                .setHoverText(readLines)
                .setSuggestCommandAction(readLines)
                .send(player);

    }

}
