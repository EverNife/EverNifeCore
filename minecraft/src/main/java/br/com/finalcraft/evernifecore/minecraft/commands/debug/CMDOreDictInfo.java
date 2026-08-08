package br.com.finalcraft.evernifecore.minecraft.commands.debug;


import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.McPermissionNodes;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers.ArgParserOreDict;
import br.com.finalcraft.evernifecore.minecraft.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.minecraft.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.PageVisualization;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@FinalCMD(
        aliases = {"fcoredictinfo","oredictinfo", "oreinfo"},
        permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
)
public class CMDOreDictInfo {

    @FCLocale(lang = LocaleType.EN_US,
            text = "§2§l ▶ §b[OredictINFO]§e - ${oredict_name}   §7§o(has ${oredict_amount} items)",
            hover = "\n§2OredictName: §e${oredict_name}" +
                    "\n§2OredictItems: §e${oredict_amount}" +
                    "\n" +
                    "\n§bClick to list every item from this OreDict!",
            click = "/${label} listItemsFrom ${oredict_name}"
    )
    private static LocaleMessage OREDICT_INFO;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §c This item do not have an OreDict!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §c Este item não possui um OreDict!")
    private static LocaleMessage THIS_ITEM_DO_NOT_HAVE_AN_OREDICT;

    @FinalCMD.SubCMD(
            subcmd = {"listItemsFrom"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List all itemIdentifiers from this OreDict!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Lista todos os itemIdentifiers deste OreDict!")
            },
            permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void listItemsFrom(FCommandSender sender, @Arg("<oreDict>") OreDictEntry oreDictEntry, @Arg("[page]") PageVisualization pageVisualization) {

        List<ItemStack> itemStacks = oreDictEntry.getItemStacks();

        //Anonymous on purpose: what this page holds depends on <oreDict>, so there is no name that
        //could mean the same thing tomorrow - it is paged through the reader's own session.
        PageViewer.of(ItemStack.class)
                .source(() -> itemStacks)
                .unlimitedEntries()
                .orderBy(itemStack -> FCItemUtils.getMinecraftIdentifier(itemStack)).ascending()
                .setFormatLine(itemStack -> {
                    return new FancySegment("§7#  ${number}:   §a${value}").setClickSuggest("${value}");
                })
                .build()
                .send(pageVisualization, sender);
    }

    @FinalCMD.SubCMD(
            subcmd = {"list"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List all oredicts!"),
            },
            permission = McPermissionNodes.EVERNIFECORE_COMMAND_OREINFO
    )
    public void list(FCommandSender sender, String label, @Arg("[startsWith]") String startsWith) {

        final List<OreDictEntry> filteredEntries = ArgParserOreDict.CACHED_OREDICT_ENTRIES.getValue();

        if (startsWith != null){
            filteredEntries.removeIf(oreDictEntry -> !FCStringUtil.startsWithIgnoreCase(oreDictEntry.getOreName(), startsWith));
        }

        PageViewer.of(OreDictEntry.class)
                .source(() -> filteredEntries)
                .unlimitedEntries()
                .orderBy(oreDict -> oreDict.getOreName()).ascending()
                .setFormatLine(
                        new FancySegment("§7#  ${number}: (${oredict_amount})  §a${value}")
                        .setClickCommand(OREDICT_INFO.getFancyText(sender).getClickActionText())
                        .setHover(OREDICT_INFO.getFancyText(sender).getHoverText())
                )
                .addRowPlaceholder("oredict_amount", entry -> entry.getItemStacks().size())
                .addRowPlaceholder("oredict_name", entry -> entry.getOreName())
                .addRowPlaceholder("label", oreDictEntry -> label)
                .theme(PageTheme.classic().withTotalCount())
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
                    .addPlaceholder("oredict_name", oreName)
                    .addPlaceholder("oredict_amount", itemStacks.size())
                    .addPlaceholder("label", label)
                    .send(player);
        });

    }


}
