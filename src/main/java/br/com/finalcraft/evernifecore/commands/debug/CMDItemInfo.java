package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CMDItemInfo {

    @FinalCMD(
            aliases = {"iteminfo"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void onCommand(Player player, String label, MultiArgumentos argumentos) {

        ItemStack heldItem = FCBukkitUtil.getPlayersHeldItem(player);

        if (heldItem == null){
            FCMessageUtil.needsToBeHoldingItem(player);
            return;
        }

        String mcIdentifier = FCItemUtils.getMinecraftIdentifier(heldItem);
        String bukkitIdentifier = FCItemUtils.getBukkitIdentifier(heldItem);

        FancyText.of("§7§o[INFO] ").setHoverText("\nMinecraft Identifier: " + mcIdentifier + "\n").setSuggestCommandAction(mcIdentifier)
                .append(bukkitIdentifier).setSuggestCommandAction(bukkitIdentifier)
                .send(player);
    }

}
