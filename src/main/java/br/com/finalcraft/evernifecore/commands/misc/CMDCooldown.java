package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import org.bukkit.command.CommandSender;

@FinalCMD(
        aliases = {"cooldown", "cooldowns"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_COOLDOWN
)
public class CMDCooldown {

    @FinalCMD.SubCMD(
            subcmd = "reset",
            usage = "%name% <CooldownID>",
            desc = "Recarrega um Cooldown Especifico"
    )
    public void reset(CommandSender sender, String label, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1)){
            helpLine.sendTo(sender);
            return;
        }

        Cooldown cooldown = Cooldown.of(argumentos.getStringArg(1));
        if (cooldown == null){
            sender.sendMessage("§2§l ▶ §aO cooldown " + argumentos.getStringArg(1) + " não existe!");
            return;
        }

        if (!cooldown.isInCooldown()){
            sender.sendMessage("§2§l ▶ §aO cooldown " + argumentos.getStringArg(1) + " não está em Cooldown!");
            return;
        }

        cooldown.stop();
        sender.sendMessage("§2§l ▶ §aCooldown " + argumentos.getStringArg(1) + " removido com sucesso!");
        return;
    }


    @FinalCMD.SubCMD(
            subcmd = "reload",
            desc = "Recarrega todos os Cooldowns"
    )
    public boolean reload(CommandSender sender, String label, MultiArgumentos argumentos) {
        ConfigManager.reloadCooldownConfig();
        sender.sendMessage("§2§l ▶ §aTodos os Cooldowns foram recarregados com sucesso!");
        return true;
    }
}
