package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.stream.Collectors;

public class CMDItemInfo {

    @FCLocale(lang = LocaleType.EN_US, text = "§d ● §eLocalizedName: §a%localized_name%",
            hover = "§7Click to copy: \n\n§7 ✯ §a%uncolorfied_localized_name%§r",
            runCommand = "%uncolorfied_localized_name%",
            clickActionType = ClickActionType.SUGGEST_COMMAND
    )
    private static LocaleMessage LOCALIZED_NAME;

    @FCLocale(lang = LocaleType.EN_US, text = "§d ● §eDisplayName: §r%display_name%",
            hover = "§7Click to copy: \n\n§7 ✯ §a%uncolorfied_display_name%§r",
            runCommand = "%uncolorfied_display_name%",
            clickActionType = ClickActionType.SUGGEST_COMMAND
    )
    private static LocaleMessage DISPLAY_NAME;

    @FCLocale(lang = LocaleType.EN_US, text = "\n§d ● §eMCIdentifier: §a%mc_identifier%",
            hover = "§7Click to copy: \n\n§7 ✯ §a%mc_identifier%§r",
            runCommand = "%mc_identifier%",
            clickActionType = ClickActionType.SUGGEST_COMMAND
    )
    private static LocaleMessage MC_IDENTIFIER;

    @FCLocale(lang = LocaleType.EN_US, text = "\n§7§l(§b▼§7§l)",
            hover = "§7Click to copy: \n§7 ✯ §a%bukkit_identifier%§r",
            runCommand = "%bukkit_identifier%",
            clickActionType = ClickActionType.SUGGEST_COMMAND,
            children = {
                    @FCLocale.Child(
                            text = "§b%item_data_part%",
                            hover = "§7Click to copy: \n§b%item_data_part%",
                            runCommand = "%item_data_part%",
                            clickActionType = ClickActionType.SUGGEST_COMMAND
                    )
            }
    )
    private static LocaleMessage BUKKIT_IDENTIFIER;

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

        player.sendMessage("§7§m" + FCColorUtil.stripColor(FCTextUtil.straightLineOf("-")));
        if (NMSUtils.get() != null){
            String localizedName = FCItemUtils.getLocalizedName(heldItem);
            String uncolorfiedLocalizedName = FCColorUtil.decolorfy(localizedName);
            LOCALIZED_NAME
                    .addPlaceholder("%localized_name%", localizedName)
                    .addPlaceholder("%uncolorfied_localized_name%", uncolorfiedLocalizedName)
                    .send(player);
        }

        String displayName = FCItemUtils.getDisplayName(heldItem);
        if (displayName != null){
            String uncolorfiedDisplayName = FCColorUtil.decolorfy(displayName);
            DISPLAY_NAME
                    .addPlaceholder("%display_name%", displayName)
                    .addPlaceholder("%uncolorfied_display_name%", uncolorfiedDisplayName)
                    .send(player);
        }

        if (NMSUtils.get() != null){
            MC_IDENTIFIER
                    .addPlaceholder("%mc_identifier%", FCItemUtils.getMinecraftIdentifier(heldItem))
                    .send(player);
        }

        String itemDataPart = ItemDataPart.readItem(heldItem).stream()
                .collect(Collectors.joining("\n - ","\n - ",""));

        BUKKIT_IDENTIFIER
                .addPlaceholder("%bukkit_identifier%", FCItemUtils.getBukkitIdentifier(heldItem))
                .addPlaceholder("%item_data_part%", itemDataPart)
                .send(player);

    }

}
