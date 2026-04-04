package br.com.finalcraft.evernifecore.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class FinalCMDPluginCommand {

    protected final ECPluginData owningPlugin;
    protected final FinalCMDData finalCMD;
    protected final FCDefaultExecutor executor;
    protected final @Nullable CMDMethodInterpreter mainInterpreter;
    protected final List<CMDMethodInterpreter> subCommands = new ArrayList<>();
    //
    //KEY: 0
    //VALUES: 0
    //   SUB_CMD  ? /sharelag add <Player>
    //   SUB_CMD  ? /sharelag remove <Player>
    //   SUB_CMD  ? /sharelag list
    //

    protected String permission;
    protected HelpContext helpContext;// Immutable Context from all HelpLines from all SubCmds (come from list 'helpLineList' bellow)
    protected List<Field> localeMessageFields = new ArrayList<>();
    protected transient IPlatformCMD platformCommand; //The actual command.class inside the platform

    public static final String DEFAULT_USAGE = "§3§l ▶ §a/§e%label% ";

    public FinalCMDPluginCommand(@Nonnull ECPluginData owningPlugin, @Nonnull FinalCMDData finalCMD, @Nullable CMDMethodInterpreter mainInterpreter) {
        Validate.notNull(owningPlugin, "OwningPlugin is null!");
        Validate.notNull(finalCMD, "FinalCMD is null!");
        Validate.isTrue(!finalCMD.getLabels()[0].isEmpty(), "Name is empty!");

        this.owningPlugin = owningPlugin;
        this.finalCMD = finalCMD;
        this.executor = new FCDefaultExecutor(this);
        this.mainInterpreter = mainInterpreter;
    }

    public String getPrimaryLabel(){
        return finalCMD.getLabels()[0];
    }

    public String[] getExtraLabels(){
        return Arrays.copyOfRange(finalCMD.getLabels(), 1, finalCMD.getLabels().length);
    }

    public void addLocaleMessages(List<Field> localeMessages){
        localeMessages.forEach(field -> field.setAccessible(true));
        localeMessageFields.addAll(localeMessages);
    }

    /**
     * Unregister all commands that are registered on the server with
     * the name of this command!
     *
     * @return true if the command has been successfully registered
     */
    public boolean registerCommand() {
        FinalCMDManager.unregisterCommand(this.getPrimaryLabel(), this.getOwningPlugin());
        for (String alias : this.getExtraLabels()) {
            FinalCMDManager.unregisterCommand(alias, this.getOwningPlugin());
        }

        //Sort all Methods based on the First Label's Name
        Collections.sort(subCommands, Comparator.comparing(cmdMethodInterpreter -> cmdMethodInterpreter.getLabels()[0]));

        this.helpContext = new HelpContext(finalCMD.getHelpHeader(), this);

        String calculatedPermission = finalCMD.getPermission();
        if (mainInterpreter != null && mainInterpreter.getCmdData().getPermission() != null && !mainInterpreter.getCmdData().getPermission().isEmpty()){
            calculatedPermission = mainInterpreter.getCmdData().getPermission();
        }else if (subCommands.size() > 0){
            if (subCommands.stream().map(subCommand -> subCommand.getCmdData().getPermission()).filter(String::isEmpty).findFirst().isPresent()){
                calculatedPermission = null; //If there is at least one subcommand that has no permission, then the main command will have no permission
            }else {
                calculatedPermission = subCommands.stream()
                        .map(subCommand -> subCommand.getCmdData().getPermission())
                        .filter(Objects::nonNull)
                        .filter(permission -> !permission.isEmpty())
                        .collect(Collectors.joining(";"));
            }
        }

        this.permission = calculatedPermission != null && !calculatedPermission.isEmpty()
                        ? calculatedPermission
                        : null;

        return EverNifeCore.getPlatform().registerCommand(this);
    }

    public CMDMethodInterpreter getSubCommand(String firstArg) {

        for (CMDMethodInterpreter methodInterpreter : subCommands) {
            for (String label : methodInterpreter.getLabels()) {
                if (label.equalsIgnoreCase(firstArg)){
                    return methodInterpreter;
                }
            }
        }

        return null;
    }

    public void addSubCommand(CMDMethodInterpreter executor) {
        subCommands.add(executor);
    }

    public ECPluginData getOwningPlugin() {
        return owningPlugin;
    }

    /**
     * @param sender sender
     * @param alias  alias used
     * @param args   argument of the command
     *
     * @return a list of possible values
     */
    public List<String> tabComplete(FCommandSender sender, String alias, String[] args) {
        int index = args.length - 1;

        boolean isPlayer = sender instanceof FPlayer;

        //The TabComplete is based on the FirstArg.
        CMDMethodInterpreter interpreter = (args.length == 0 || args[0].isEmpty()) ? null : getSubCommand(args[0]);
        if (interpreter == null && mainInterpreter != null && ((FinalCMDData)mainInterpreter.getCmdData()).getHelpType() == CMDHelpType.FULL){
            interpreter = mainInterpreter;
        }

        if (interpreter == null && subCommands.size() > 0){
            return subCommands.stream()
                    .filter(subCommand -> subCommand.getCmdData().getPermission().isEmpty() || sender.hasPermission(subCommand.getCmdData().getPermission())) //For the first arg of all sub commands we need ot check each permission
                    .filter(subCommand -> !subCommand.isPlayerOnly() ? true : isPlayer) //If is the console calling this tab completion, ignore the subCommand if it's a 'playerOnly' subCMD
                    .filter(subCommand -> {
                        if (subCommand.getCmdData().getCmdAccessValidations().length == 0){
                            return true;
                        }
                        CMDAccessValidation.AccessContext accessContext = new CMDAccessValidation.AccessContext(subCommand, sender);
                        for (CMDAccessValidation cmdAccessValidation : subCommand.getCmdData().getCmdAccessValidations()) {
                            if (!cmdAccessValidation.onPreTabValidation(accessContext)){
                                return false;
                            }
                        }
                        return true;
                    }) //Apply a final custom filtering, in case this cmd has a custom cmdAccessValidation
                    .map(subCommand -> subCommand.getLabels()[0])
                    .filter(s -> StringUtils.startsWithIgnoreCase(s, args[index]))
                    .collect(Collectors.toList());
        }

        if (interpreter == null || !interpreter.hasTabComplete() || (!interpreter.getCmdData().getPermission().isEmpty() && !sender.hasPermission(interpreter.getCmdData().getPermission()))){
            return new ArrayList<>();
//            return super.tabComplete(sender, alias, args); //TODO:  Implement this: No SubCommand NOR mainCommand found
        }

        ITabParser tabParser = interpreter.getTabParser(index);

        if (tabParser == null){
            return ImmutableList.of();
        }

        ITabParser.TabContext tabContext = new ITabParser.TabContext(sender, alias, args, index);

        return tabParser.tabComplete(tabContext);
    }

}
