package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.IFinalCMDExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class FCDefaultExecutor implements IFinalCMDExecutor {

    final FinalCMDPluginCommand finalCommand;
    final ExecutorInterpreter mainInterpreter;
    final br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMD;

    public FCDefaultExecutor(FinalCMDPluginCommand finalCommand, FinalCMD finalCMD) {
        this(finalCommand, null, finalCMD);
    }

    public FCDefaultExecutor(FinalCMDPluginCommand finalCommand, ExecutorInterpreter mainInterpreter, br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMD) {
        this.finalCommand = finalCommand;
        this.mainInterpreter = mainInterpreter;
        this.finalCMD = finalCMD;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if ((finalCMD.playerOnly() || (mainInterpreter!= null && mainInterpreter.playerArg)) && FCBukkitUtil.isNotPlayer(sender)){
            return true;
        }

        if (!finalCMD.permission().isEmpty() && !FCBukkitUtil.hasThePermission(sender, finalCMD.permission())){
            return true;
        }

        if (finalCMD.useDefaultHelp() != CMDHelpType.NONE && finalCommand.subCommands.size() > 0){
            String firstArg = args.length >= 1 ? args[0].toLowerCase() : "";
            switch (firstArg){
                case "":
                    if (finalCMD.useDefaultHelp() == CMDHelpType.EXCEPT_EMPTY) break;
                case "?":
                case "help":
                case "ajuda":
                    finalCommand.helpContext.sendTo(sender, label);
                    return true;
            }
        }

        MultiArgumentos argumentos = new MultiArgumentos(args, false);
        try {
            SubCommand subCommand = finalCommand.getSubCommand(args);

            if (subCommand != null){

                if ((subCommand.finalSubCMD.playerOnly() || subCommand.executorInterpreter.playerArg) && FCBukkitUtil.isNotPlayer(sender)){
                    return true;
                }

                if (!subCommand.finalSubCMD.permission().isEmpty() && !FCBukkitUtil.hasThePermission(sender, subCommand.finalSubCMD.permission())){
                    return true;
                }

                prepareLocales(sender, label);
                subCommand.executorInterpreter.invoke(sender, label, argumentos, finalCommand.helpContext, subCommand.helpLine.setLabelUsed(label));
            }else {

                prepareLocales(sender, label);
                if (mainInterpreter == null){
                    onCommand(sender, label);
                }else {
                    mainInterpreter.invoke(sender, label, argumentos, finalCommand.helpContext, finalCommand.mainHelpLine.setLabelUsed(label));
                }
            }
        } catch (IllegalAccessException e) {
            finalCommand.getPlugin().getLogger().warning("Failed to execute the FinalCMD, maybe args are wrong?");
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            finalCommand.getPlugin().getLogger().warning("Failed to execute the FinalCMD, maybe args are wrong?");
            throw new RuntimeException(e);
        }

        return true;
    }

    private void prepareLocales(CommandSender sender, String label) throws IllegalAccessException {
        for (Field localeMessageField : this.finalCommand.localeMessageFields) {
            LocaleMessageImp localeMessage = (LocaleMessageImp) localeMessageField.get(null);
            localeMessage.getContextPlaceholders().clear();
            localeMessage.getContextPlaceholders().put("%label%",label);
            if (sender instanceof Player) localeMessage.getContextPlaceholders().put("%player%",((Player) sender).getName());
        }
    }

    public void onCommand(CommandSender sender, String label) {
        sender.sendMessage("§cErro de parametros, por favor use /" + label + " help");
    }
}
