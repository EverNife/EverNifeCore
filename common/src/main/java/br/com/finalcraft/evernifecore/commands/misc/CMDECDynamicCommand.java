package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommandManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleScope;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;

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
    public void onCommand(FCommandSender sender, MultiArgumentos argumentos) {

        UUID cmdIdentifier = argumentos.get(0).getUUID();
        if (cmdIdentifier == null){
            FCMessageUtil.needsToBeUUID(sender, argumentos.getStringArg(0));
            return;
        }

        DynamicCommand cmd = DynamicCommandManager.find(cmdIdentifier);

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

        //the core texts the action sends speak the language of the plugin that made the link
        try (LocaleScope ignored = LocaleScope.enter(cmd.getOwner())) {
            cmd.runAction(sender);
        }

        if (cmd.shouldRemove(sender)){
            DynamicCommandManager.remove(cmdIdentifier);
        }
    }

}
