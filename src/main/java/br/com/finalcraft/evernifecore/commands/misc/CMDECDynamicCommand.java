package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommandManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class CMDECDynamicCommand {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThis UUID has no command on it!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cEssa UUID não possui nenhum comando vinculada a ela!")
    private static LocaleMessage UUID_HAS_NO_COMMAND;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou cannot execute this action anymore because it has expired!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê não pode mais executar esse ação porque ele expirou!")
    private static LocaleMessage COMMAND_EXPIRED;

    @FinalCMD(
            aliases = {"ecdcmd"}
    )
    public void onCommand(CommandSender sender, MultiArgumentos argumentos) {

        UUID cmdIdentifier = argumentos.get(0).getUUID();
        if (cmdIdentifier == null){
            FCMessageUtil.needsToBeUUID(sender, argumentos.getStringArg(0));
            return;
        }

        DynamicCommand cmd = DynamicCommandManager.DYNAMIC_COMMANDS.get(cmdIdentifier);

        if (cmd == null){
            UUID_HAS_NO_COMMAND.send(sender);
            return;
        }

        if (cmd.getCooldown().isInCooldown()){
            COMMAND_EXPIRED.send(sender);
            return;
        }

        if (!cmd.shouldRun(sender)){
            return;
        }

        cmd.incrementRun();

        cmd.runAction(sender);

        if (cmd.shouldRemove(sender)){
            DynamicCommandManager.DYNAMIC_COMMANDS.remove(cmdIdentifier);
        }
    }

}
