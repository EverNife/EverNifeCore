package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.IPlatformCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFCommandSender;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class McFinalCMDPluginCommand extends Command implements PluginIdentifiableCommand, IPlatformCMD {

    private final FinalCMDPluginCommand finalCMDPluginCommand;
    private final JavaPlugin owningPlugin;

    public JavaPlugin getJavaPlugin(){
        return (JavaPlugin) this.finalCMDPluginCommand.getOwningPlugin().getPlugin();
    }

    public McFinalCMDPluginCommand(FinalCMDPluginCommand finalCMDPluginCommand) {
        super(finalCMDPluginCommand.getPrimaryLabel());
        this.finalCMDPluginCommand = finalCMDPluginCommand;
        this.owningPlugin = (JavaPlugin) finalCMDPluginCommand.getOwningPlugin().getPlugin();

        setAliases(Arrays.asList(finalCMDPluginCommand.getFinalCMD().getLabels()));
        setLabel(getName());
    }

    @Override
    public boolean testPermission(@Nonnull CommandSender target) {
        if (testPermissionSilent(target)) {
            return true;
        }

        FCMessageUtil.needsThePermission(FCBukkitUtil.adapt(target), getPermission());
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

        Plugin owningPlugin = (org.bukkit.plugin.Plugin) finalCMDPluginCommand.getOwningPlugin().getPlugin();

        if (!owningPlugin.isEnabled()) {
            throw new CommandException("Cannot execute command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName() + " - plugin is disabled.");
        }

        try {
            final FCommandSender fCommandSender;
            if (sender instanceof Player player){
                fCommandSender = FCBukkitUtil.adapt(player);
            }else {
                fCommandSender = FCBukkitUtil.adapt(sender);
            }

            finalCMDPluginCommand.getExecutor().onCommand(fCommandSender, commandLabel, args);
            success = true;
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
        CMDMethodInterpreter interpreter = (args.length == 0 || args[0].isEmpty())
                ? null
                : this.finalCMDPluginCommand.getSubCommand(args[0]);

        if (interpreter == null && this.finalCMDPluginCommand.getMainInterpreter() != null && ((FinalCMDData)this.finalCMDPluginCommand.getMainInterpreter().getCmdData()).getHelpType() == CMDHelpType.FULL){
            interpreter = this.finalCMDPluginCommand.getMainInterpreter();
        }

        if (interpreter == null && this.finalCMDPluginCommand.getSubCommands().size() > 0){
            return this.finalCMDPluginCommand.getSubCommands().stream()
                    .filter(subCommand -> subCommand.getCmdData().getPermission().isEmpty() || sender.hasPermission(subCommand.getCmdData().getPermission())) //For the first arg of all sub commands we need ot check each permission
                    .filter(subCommand -> !subCommand.isPlayerOnly() ? true : isPlayer) //If is the console calling this tab completion, ignore the subCommand if it's a 'playerOnly' subCMD
                    .filter(subCommand -> {
                        if (subCommand.getCmdData().getCmdAccessValidations().length == 0){
                            return true;
                        }
                        CMDAccessValidation.AccessContext accessContext = new CMDAccessValidation.AccessContext(subCommand, MinecraftFCommandSender.of(sender));
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

        ITabParser.TabContext tabContext = new ITabParser.TabContext(FCBukkitUtil.adapt(sender), alias, args, index);

        return tabParser.tabComplete(tabContext);
    }
}
