package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.EverNifeCoreReloadEvent;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

@FinalCMD(
        aliases = {"evernifecore","ecore"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_RELOAD
)
public class CoreCommand {

    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Fully reload EverNifeCore! Including all playerdata of all players!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Da reload no EverNifeCore! Incluindo todos os PlayerData de todos os jogadores!")
            }
    )
    public static void reload(CommandSender sender, MultiArgumentos argumentos){
        ConfigManager.initialize(EverNifeCore.instance);
        FCMessageUtil.pluginHasBeenReloaded(sender, EverNifeCore.instance.getName());
        EverNifeCoreReloadEvent reloadEvent = new EverNifeCoreReloadEvent();
        Bukkit.getServer().getPluginManager().callEvent(reloadEvent);
    }

}
