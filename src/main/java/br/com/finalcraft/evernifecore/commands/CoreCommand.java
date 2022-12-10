package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.pageviwer.PageViewer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;

@FinalCMD(
        aliases = {"evernifecore","ecore"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_RELOAD
)
public class CoreCommand {

    @FinalCMD.SubCMD(
            subcmd = "info",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show info of the EverNifeCore and its addons!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra informações do EverNifeCore e de seus addons!")
            }
    )
    public void info(CommandSender sender, @Arg(name = "[page]", context = "[1:*]") Integer page){
        PageViewer.targeting(ECPluginData.class)
                .withSuplier(() -> new ArrayList<>(ECPluginManager.getECPluginsMap().values()))
                .extracting(ecPluginData -> ecPluginData.getPlugin().getName())
                .setFormatLine(
                        FancyText.of("§7# %number%: §e§l◆ §a %value% §7§o(%version%)").setHoverText("%plugin_info%")
                                .append("%can_update%").setHoverText("§aClique to go to DownloadLink").setOpenLinkAction("%update_link%")
                )
                .addPlaceholder("%version%", ecPlugin -> ecPlugin.getPlugin().getDescription().getVersion())
                .addPlaceholder("%can_update%", ecPlugin -> ecPlugin.hasUpdate() ? "§b  [Update]" : "")
                .addPlaceholder("%update_link%", ecPlugin -> ecPlugin.hasUpdate() ? ecPlugin.getUpdateLink() : "")
                .addPlaceholder("%plugin_info%", ecPlugin -> {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("\n§d ▲ Name: §a" + ecPlugin.getPlugin().getName());
                    stringBuilder.append("\n§d ▲ Version: §a" + ecPlugin.getPlugin().getDescription().getVersion());
                    stringBuilder.append("\n\n§d ▲ Is Up To Date: " + (ecPlugin.hasUpdate() ? "§c" : "§b") + !ecPlugin.hasUpdate());
                    stringBuilder.append("\n");
                    return stringBuilder.toString();
                })
                .build()
                .send(page, sender);
    }

    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Fully reload EverNifeCore! Including all PlayerData of all players!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Da reload no EverNifeCore! Incluindo todos os PlayerData de todos os jogadores!")
            }
    )
    public void reload(CommandSender sender){
        ECPluginManager.reloadPlugin(sender, EverNifeCore.instance);
    }

}
