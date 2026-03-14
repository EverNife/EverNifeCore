package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;

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

    @FCLocale(lang = LocaleType.EN_US, text = "§d ● §eHyIdentifier: §a%item_id%",
            hover = "§7Click to copy: \n\n§7 ✯ §a%item_id%§r",
            runCommand = "%item_id%",
            clickActionType = ClickActionType.SUGGEST_COMMAND
    )
    private static LocaleMessage ITEM_ID;

    @FCLocale(lang = LocaleType.EN_US, text = "\n§7§l(§b▼§7§l)",
            hover = "§7Click to copy: \n§7 ✯ §a%hy_identifier%§r",
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
    private static LocaleMessage ITEM_DATA_PART;

    @FinalCMD(
            aliases = {"eciteminfo","iteminfo"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ITEMINFO
    )
    public void iteminfo(HytaleFPlayer player, ItemStack heldItem) {

        player.sendMessage("&7" + FCColorUtil.stripColor(FCTextUtil.straightLineOf("---------------------------------")));

        String localizedName = FCItemUtils.getLocalizedName(heldItem);
        String uncolorfiedLocalizedName = FCColorUtil.decolorfy(localizedName);
        LOCALIZED_NAME
                .addPlaceholder("%localized_name%", localizedName)
                .addPlaceholder("%uncolorfied_localized_name%", uncolorfiedLocalizedName)
                .send(player);

//        if (displayName != null){
//            String uncolorfiedDisplayName = FCColorUtil.decolorfy(displayName);
//            DISPLAY_NAME
//                    .addPlaceholder("%display_name%", displayName)
//                    .addPlaceholder("%uncolorfied_display_name%", uncolorfiedDisplayName)
//                    .send(player);
//        }

        ITEM_ID
                .addPlaceholder("%item_id%", heldItem.getItemId())
                .send(player);

        String itemDataPart = ItemDataPart.readItem(heldItem).stream()
                .collect(Collectors.joining("\n - ","\n - ",""));

        ITEM_DATA_PART
                .addPlaceholder("%hy_identifier%", heldItem.getItemId())
                .addPlaceholder("%item_data_part%", itemDataPart)
                .send(player);

    }

}
