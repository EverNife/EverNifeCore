package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.EverNifeCoreReloadEvent;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

@FinalCMD(
        aliases = {"evernifecore","ecore"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_RELOAD
)
public class CoreCommand {

    @FinalCMD.SubCMD(
            subcmd = "reload",
            desc = "Fully reload EverNifeCore! Including all playerdata of all players!"
    )
    public static void reload(CommandSender sender, MultiArgumentos argumentos){
        ConfigManager.initialize(EverNifeCore.instance);
        sender.sendMessage("§2§l ▶ §aEverNifeCore was realoded!");
        EverNifeCoreReloadEvent reloadEvent = new EverNifeCoreReloadEvent();
        Bukkit.getServer().getPluginManager().callEvent(reloadEvent);
    }

}
