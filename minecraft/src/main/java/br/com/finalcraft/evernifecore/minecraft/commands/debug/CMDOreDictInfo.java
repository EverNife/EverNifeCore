package br.com.finalcraft.evernifecore.minecraft.commands.debug;


import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.McPermissionNodes;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers.ArgParserOreDict;
import br.com.finalcraft.evernifecore.minecraft.guis.gui.OredictViewerGui;
import br.com.finalcraft.evernifecore.minecraft.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.minecraft.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.PageVizualization;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@FinalCMD(
        aliases = {"fcoredictinfo","oredictinfo", "oreinfo"},
        permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
)
public class CMDOreDictInfo {

    @FCLocale(lang = LocaleType.EN_US,
            text = "§2§l ▶ §b[OredictINFO]§e - %oredict_name%   §7§o(has %oredict_amount% items)",
            hover = "\n§2OredictName: §e%oredict_name%" +
                    "\n§2OredictItems: §e%oredict_amount%" +
                    "\n" +
                    "\n§bClick to open a menu with all items from this OreDict!",
            click = "/%label% menu %oredict_name%"
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
            permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
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
            permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void listItemsFrom(FCommandSender sender, @Arg(name = "<oreDict>") OreDictEntry oreDictEntry, @Arg(name = "[page]") PageVizualization pageVizualization) {

        List<ItemStack> itemStacks = oreDictEntry.getItemStacks();

        PageViewer.targeting(ItemStack.class)
                .withSuplier(() -> itemStacks)
                .extracting(itemStack -> FCItemUtils.getMinecraftIdentifier(itemStack))
                .setFormatLine(itemStack -> {
                    return new FancySegment("§7#  %number%:   §a%value%").clickSuggest("%value%");
                })
                .build()
                .send(pageVizualization, sender);
    }

    @FinalCMD.SubCMD(
            subcmd = {"list"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List all oredicts!"),
            },
            permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void list(FCommandSender sender, String label, @Arg(name = "[startsWith]") String startsWith) {

        final List<OreDictEntry> filteredEntries = ArgParserOreDict.CACHED_OREDICT_ENTRIES.getValue();

        if (startsWith != null){
            filteredEntries.removeIf(oreDictEntry -> !FCStringUtil.startsWithIgnoreCase(oreDictEntry.getOreName(), startsWith));
        }

        PageViewer.targeting(OreDictEntry.class)
                .withSuplier(() -> filteredEntries)
                .extracting(oreDict -> oreDict.getOreName())
                .setFormatLine(
                        new FancySegment("§7#  %number%: (%oredict_amount%)  §a%value%")
                        .clickCommand(OREDICT_INFO.getFancyText(sender).getClickActionText())
                        .hover(OREDICT_INFO.getFancyText(sender).getHoverText())
                )
                .addPlaceholder("%oredict_amount%", entry -> entry.getItemStacks().size())
                .addPlaceholder("%oredict_name%", entry -> entry.getOreName())
                .addPlaceholder("%label%", oreDictEntry -> label)
                .setIncludeTotalCount(true)
                .setLineEnd(-1)
                .build()
                .send(sender);
    }

    @FinalCMD.SubCMD(
            subcmd = {"hand"},
            permission = McPermissionNodes.EVERNIFECORE_COMMAND_BLOCKINFO
    )
    public void onCommand(String label, FPlayer player, ItemStack heldItem) {

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
