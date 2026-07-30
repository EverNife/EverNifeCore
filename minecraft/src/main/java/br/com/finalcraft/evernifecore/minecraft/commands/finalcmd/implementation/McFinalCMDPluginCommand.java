package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.IPlatformCMD;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

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
        //Straight to the shared logic, with no platform fallback behind it: Bukkit's own completion
        //answers online player names to ANY position the tree could not place, which is a wrong answer
        //wearing the face of a right one. An empty list is the truth.
        return finalCMDPluginCommand.tabComplete(FCBukkitUtil.adapt(sender), alias, args);
    }
}
