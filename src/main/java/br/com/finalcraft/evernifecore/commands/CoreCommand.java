package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.pageviwer.PageViewer;
import org.bukkit.command.CommandSender;

@FinalCMD(
        aliases = {"evernifecore","ecore"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_RELOAD
)
public class CoreCommand {

    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Fully reload EverNifeCore! Including all PlayerData of all players!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Da reload no EverNifeCore! Incluindo todos os PlayerData de todos os jogadores!")
            }
    )
    public void reload(CommandSender sender){
        ECPluginManager.reloadPlugin(sender, EverNifeCore.instance, () -> {
            ConfigManager.initialize(EverNifeCore.instance);
        });
    }

}
