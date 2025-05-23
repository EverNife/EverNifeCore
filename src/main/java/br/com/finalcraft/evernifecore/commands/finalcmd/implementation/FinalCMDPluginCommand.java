package br.com.finalcraft.evernifecore.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.IFinalCMDExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class FinalCMDPluginCommand extends Command implements PluginIdentifiableCommand{

    public final JavaPlugin owningPlugin;
    public final FinalCMDData finalCMD;
    public final IFinalCMDExecutor executor;
    public final @Nullable CMDMethodInterpreter mainInterpreter;
    public final List<CMDMethodInterpreter> subCommands = new ArrayList<>();
    //
    //KEY: 0
    //VALUES: 0
    //   SUB_CMD  ? /sharelag add <Player>
    //   SUB_CMD  ? /sharelag remove <Player>
    //   SUB_CMD  ? /sharelag list
    //

    public HelpContext helpContext;// Immutable Context from all HelpLines from all SubCmds (come from list 'helpLineList' bellow)
    public List<Field> localeMessageFields = new ArrayList<>();

    public static final String DEFAULT_USAGE = "§3§l ▶ §a/§e%label% ";

    public FinalCMDPluginCommand(@NotNull JavaPlugin owningPlugin, @NotNull FinalCMDData finalCMD, @Nullable CMDMethodInterpreter mainInterpreter) {
        super(finalCMD.getLabels()[0]);

        Validate.notNull(owningPlugin, "OwningPlugin is null!");
        Validate.notNull(finalCMD, "FinalCMD is null!");
        Validate.isTrue(!finalCMD.getLabels()[0].isEmpty(), "Name is empty!");

        this.owningPlugin = owningPlugin;
        this.finalCMD = finalCMD;
        this.executor = new FCDefaultExecutor(this);
        this.mainInterpreter = mainInterpreter;

        setAliases(Arrays.asList(finalCMD.getLabels()));
        setLabel(getName());
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
        FinalCMDManager.unregisterCommand(this.getName(), this.owningPlugin);
        for (String alias : this.getAliases()) {
            FinalCMDManager.unregisterCommand(alias, this.owningPlugin);
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
        this.setPermission(
                calculatedPermission != null && !calculatedPermission.isEmpty()
                        ? calculatedPermission
                        : null
        );

        return FinalCMDManager.getCommandMap().register(owningPlugin.getName(), this);
    }

    @Override
    public boolean testPermission(@NotNull CommandSender target) {
        if (testPermissionSilent(target)) {
            return true;
        }

        FCMessageUtil.needsThePermission(target, getPermission());
        return false;
    }

    /**
     * @return plugin responsible for the command
     */
    @Override
    public JavaPlugin getPlugin() {
        return this.owningPlugin;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        final boolean success;

        if (!owningPlugin.isEnabled()) {
            throw new CommandException("Cannot execute command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName() + " - plugin is disabled.");
        }

        try {
            success = executor.onCommand(sender, this, commandLabel, args);
        } catch (Throwable ex) {
            throw new CommandException("Unhandled exception executing command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName(), ex);
        }

        if (!success && usageMessage.length() > 0) {
            for (String line : usageMessage.replace("<command>", commandLabel).split("\n", -1)) {
                sender.sendMessage(line);
            }
        }

        return success;
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

    /**
     * @param sender sender
     * @param alias  alias used
     * @param args   argument of the command
     *
     * @return a list of possible values
     */
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {

        int index = args.length - 1;

        boolean isPlayer = sender instanceof Player;

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
            return super.tabComplete(sender, alias, args); //No SubCommand NOR mainCommand found
        }

        ITabParser tabParser = interpreter.getTabParser(index);

        if (tabParser == null){
            return ImmutableList.of();
        }

        ITabParser.TabContext tabContext = new ITabParser.TabContext(sender, alias, args, index);

        return tabParser.tabComplete(tabContext);
    }

}
