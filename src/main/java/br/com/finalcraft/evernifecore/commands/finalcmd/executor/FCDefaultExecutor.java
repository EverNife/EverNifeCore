package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.IFinalCMDExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class FCDefaultExecutor implements IFinalCMDExecutor {

    @FCLocale(lang = LocaleType.EN_US, text = "§cParameters error, please use /%label% help")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cErro de parâmetros, por favor use /%label% help")
    public static LocaleMessage PARAMETER_ERROR;

    private final @Nonnull FinalCMDPluginCommand finalCommand;
    private final FinalCMDData finalCMD;

    public FCDefaultExecutor(@Nonnull FinalCMDPluginCommand finalCommand) {
        this.finalCommand = finalCommand;
        this.finalCMD = finalCommand.finalCMD;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if ((finalCommand.mainInterpreter != null && finalCommand.mainInterpreter.isPlayerOnly()) && FCBukkitUtil.isNotPlayer(sender)){
            return true;
        }

        if (!finalCMD.getPermission().isEmpty() && !FCBukkitUtil.hasThePermission(sender, finalCMD.getPermission())){
            return true;
        }

        if (finalCMD.getHelpType() != CMDHelpType.NONE && finalCommand.subCommands.size() > 0){
            String firstArg = args.length >= 1 ? args[0].toLowerCase() : "";
            switch (firstArg){
                case "":
                    if (finalCMD.getHelpType() == CMDHelpType.EXCEPT_EMPTY) break;
                case "?":
                case "help":
                case "ajuda":
                    finalCommand.helpContext.sendTo(sender, label);
                    return true;
            }
        }

        MultiArgumentos argumentos = new MultiArgumentos(args, false);
        String subCommandName = argumentos.getStringArg(0);
        CMDMethodInterpreter subCommand = null;
        try {
            subCommand = finalCommand.getSubCommand(subCommandName);

            if (subCommand != null){

                if ((subCommand.isPlayerOnly() || subCommand.isPlayerOnly()) && FCBukkitUtil.isNotPlayer(sender)){
                    return true;
                }

                if (!subCommand.getCmdData().getPermission().isEmpty() && !FCBukkitUtil.hasThePermission(sender, subCommand.getCmdData().getPermission())){
                    return true;
                }

                prepareClassLocales(sender, label);

                if (subCommand.getCmdData().getCmdAccessValidations().length > 0){
                    CMDAccessValidation.AccessContext accessContext = new CMDAccessValidation.AccessContext(subCommand, sender);
                    for (CMDAccessValidation cmdAccessValidation : subCommand.getCmdData().getCmdAccessValidations()) {
                        if (cmdAccessValidation.onPreCommandValidation(accessContext) == false){
                            //We do not notify it here, as the player is intended to be notified inside the cmdAccessValidation
                            return true;
                        }
                    }
                }
                subCommand.invoke(sender, label, argumentos, finalCommand.helpContext, subCommand.getHelpLine().setLabelsUsed(label, subCommandName));
            }else {

                prepareClassLocales(sender, label);
                if (finalCommand.mainInterpreter == null){
                    PARAMETER_ERROR.addPlaceholder("%label%", label).send(sender);
                }else {

                    if (finalCommand.mainInterpreter.getCmdData().getCmdAccessValidations().length > 0){
                        CMDAccessValidation.AccessContext accessContext = new CMDAccessValidation.AccessContext(finalCommand.mainInterpreter, sender);
                        for (CMDAccessValidation cmdAccessValidation : finalCommand.mainInterpreter.getCmdData().getCmdAccessValidations()) {
                            if (cmdAccessValidation.onPreCommandValidation(accessContext) == false){
                                //We do not notify it here, as the player is intended to be notified inside the cmdAccessValidation
                                return true;
                            }
                        }
                    }
                    finalCommand.mainInterpreter.invoke(sender, label, argumentos, finalCommand.helpContext, finalCommand.mainInterpreter.getHelpLine().setLabelsUsed(label, subCommandName));
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            String commandInfo = getCommandInfo(subCommand != null ? subCommand : finalCommand.mainInterpreter, label, subCommandName, args);
            finalCommand.getPlugin().getLogger().severe("Failed to execute the FinalCMD: " + commandInfo);
            throw new RuntimeException(e);
        }

        return true;
    }

    private void prepareClassLocales(CommandSender sender, String label) throws IllegalAccessException {
        for (Field localeMessageField : this.finalCommand.localeMessageFields) {
            LocaleMessageImp localeMessage = (LocaleMessageImp) localeMessageField.get(null);
            localeMessage.getContextPlaceholders().clear();
            localeMessage.getContextPlaceholders().put("%label%",label);
            if (sender instanceof Player) localeMessage.getContextPlaceholders().put("%player%",((Player) sender).getName());
        }
    }

    private String getCommandInfo(CMDMethodInterpreter interpreter, String label, String subCommandName, String[] args) {

        if (!subCommandName.isEmpty()){
            label = String.format("%s %s", label, subCommandName);
        }

        if (interpreter == null) {
            return String.format("</%s> [%s] \n  Args: %s", label, finalCommand.getPlugin().getName(), Arrays.toString(args));
        }
        
        String className = interpreter.getMethod().getDeclaringClass().getSimpleName();
        String methodName = interpreter.getMethod().getName();
        
        return String.format("</%s> [%s] #%s%%%s \n  Args: %s",
            label, 
            finalCommand.getPlugin().getName(), 
            className, 
            methodName, 
            java.util.Arrays.toString(args));
    }
}
