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

import java.util.HashSet;
import java.util.UUID;

public class CMDItemInfo {

    public static HashSet<UUID> INFO_HASHSET = new HashSet<>();

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §aINFO mode Enabled!")
    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §aINFO mode Ativado!")
    private static LocaleMessage INFO_MODE_ENABLED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §eINFO mode Disabled!")
    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §eINFO mode Desativado!")
    private static LocaleMessage INFO_MODE_DISABLED;

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

        String itemIdentifier = FCItemUtils.getBukkitIdentifier(heldItem);
        FancyText.of("§7§o[INFO] " + itemIdentifier)
                .setHoverText("\n" +
                        "\n Minecraft Identifier: " + FCItemUtils.getMinecraftIdentifier(heldItem) +
                        "\n")
                .setSuggestCommandAction(itemIdentifier)
                .send(player);
    }

}
