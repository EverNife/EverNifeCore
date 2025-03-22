package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserOreDict;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.guis.gui.OredictViewerGui;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.pageviwer.PageViewer;
import br.com.finalcraft.evernifecore.pageviwer.PageVizualization;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@FinalCMD(
        aliases = {"fcoredictinfo","oredictinfo", "oreinfo"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_OREINFO
)
public class CMDOreDictInfo {

    @FCLocale(lang = LocaleType.EN_US,
            text = "§2§l ▶ §b[OredictINFO]§e - %oredict_name%   §7§o(has %oredict_amount% items)",
            hover = "\n" +
                    "\n§2OredictName: §e%oredict_name%" +
                    "\n§2OredictItems: §e%oredict_amount%" +
                    "\n§bClick to open a menu with all items from this OreDict!" +
                    "\n",
            runCommand = "/%label% menu %oredict_name%"
    )
    private static LocaleMessage OREDICT_INFO;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §c This item do not have an OreDict!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §c Este item não possui um OreDict!")
    private static LocaleMessage THIS_ITEM_DO_NOT_HAVE_AN_OREDICT;

    @FinalCMD.SubCMD(
            subcmd = {"menu"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Open a menu showing all items from this OreDict!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Abre um menu mostrando todos os itens deste OreDict!")
            },
            permission = PermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void menu(PlayerData playerData, @Arg(name = "<oreDict>") OreDictEntry oreDictEntry) {

        new OredictViewerGui(oreDictEntry, playerData).open();

    }

    @FinalCMD.SubCMD(
            subcmd = {"listItemsFrom"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List all itemIdentifiers from this OreDict!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Lista todos os itemIdentifiers deste OreDict!")
            },
            permission = PermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void listItemsFrom(CommandSender sender, @Arg(name = "<oreDict>") OreDictEntry oreDictEntry, @Arg(name = "[page]") PageVizualization pageVizualization) {

        List<ItemStack> itemStacks = oreDictEntry.getItemStacks();

        PageViewer.targeting(ItemStack.class)
                .withSuplier(() -> itemStacks)
                .extracting(itemStack -> FCItemUtils.getMinecraftIdentifier(itemStack))
                .setFormatLine(itemStack -> {
                    return new FancyText("§7#  %number%:   §a%value%").setSuggestCommandAction("%value%");
                })
                .build()
                .send(pageVizualization, sender);
    }

    @FinalCMD.SubCMD(
            subcmd = {"list"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List all oredicts!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Lista todos os itemIdentifiers deste OreDict!")
            },
            permission = PermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void list(CommandSender sender, String label, @Arg(name = "[page]") PageVizualization pageVizualization) {
        PageViewer.targeting(OreDictEntry.class)
                .withSuplier(() -> ArgParserOreDict.CACHED_OREDICT_ENTRIES.getValue())
                .extracting(oreDict -> oreDict.getOreName())
                .setFormatLine(new FancyText("§7#  %number%: (%itens_count%)  §a%value%")
                        .setRunCommandAction(OREDICT_INFO.getFancyText(sender).getClickActionText())
                        .setHoverText(OREDICT_INFO.getFancyText(sender).getHoverText())
                )
                .addPlaceholder("%itens_count%", oreDictEntry1 -> oreDictEntry1.getItemStacks().size())
                .addPlaceholder("%label%", oreDictEntry -> label)
                .setIncludeTotalCount(true)
                .setLineEnd(-1)
                .build()
                .send(pageVizualization, sender);
    }

    @FinalCMD.SubCMD(
            subcmd = {"hand"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_BLOCKINFO
    )
    public void onCommand(String label, Player player, ItemStack heldItem) {

        List<String> oreNamesFrom = NMSUtils.get().getOreRegistry().getOreNamesFrom(heldItem);

        if (oreNamesFrom.size() == 0){
            THIS_ITEM_DO_NOT_HAVE_AN_OREDICT.send(player);
            return;
        }

        player.sendMessage("");

        oreNamesFrom.forEach(oreName -> {
            List<ItemStack> itemStacks = NMSUtils.get().getOreRegistry().getOreItemStacks(oreName);
            OREDICT_INFO
                    .addPlaceholder("%oredict_name%", oreName)
                    .addPlaceholder("%oredict_amount%", itemStacks.size())
                    .addPlaceholder("%label%", label)
                    .send(player);
        });

    }


}
