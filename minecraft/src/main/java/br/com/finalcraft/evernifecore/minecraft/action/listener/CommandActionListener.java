package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.CommandContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.CommandActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A player about to run a command ({@link EventSource#COMMAND}), every command of every plugin, asked where the player
 * stands with the label typed and the command's name and aliases. A denial, or a cancelled {@code Pre}, keeps the command
 * from running; one that went through posts its {@link CommandActionEvent} Post. Bukkit raises the source here, so the
 * core's command framework stays out of it ({@code ActionSupport.isNativeCommandSource()}).
 */
public final class CommandActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!McActionContexts.isDisabled(event.getPlayer().getWorld())
                && Actions.denied(context(event), CommandActionEvent.Pre.class, CommandActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterCommand(PlayerCommandPreprocessEvent event) {
        if (!McActionContexts.isDisabled(event.getPlayer().getWorld())) {
            Actions.post(CommandActionEvent.Post.class, () -> new CommandActionEvent.Post(context(event)));
        }
    }

    private static CommandContext context(PlayerCommandPreprocessEvent event) {
        return new CommandContext(McActionContexts.position(event.getPlayer().getLocation()), McActionContexts.actor(event.getPlayer()),
                event, event.getMessage(), namesOf(event.getMessage()));
    }

    /** The name and aliases of the command the line runs; empty when the server knows no such command. */
    static List<String> namesOf(String commandLine) {
        String typed = commandLine.trim().split(" ", 2)[0];
        CommandMap map = McPlatform.getCommandMap();
        //the label as typed, prefix included: essentials:home finds Essentials' home, not another plugin's
        Command command = map == null ? null : map.getCommand((typed.startsWith("/") ? typed.substring(1) : typed).toLowerCase(Locale.ROOT));
        if (command == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>(command.getAliases());
        names.add(command.getName());
        return names;
    }
}
