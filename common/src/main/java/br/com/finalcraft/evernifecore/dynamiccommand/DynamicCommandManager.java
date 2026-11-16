package br.com.finalcraft.evernifecore.dynamiccommand;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.util.collection.SelfExpiringMap;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DynamicCommandManager {

    // Time an unused dynamic-command link lingers in memory before being purged.
    // A generous upper bound (well above the intended lifetime of a confirmation link),
    // decoupled from the DynamicCommand cooldown, which is never started at scheduling
    // time and so does not describe a real lifetime here. It only bounds memory: the
    // cooldown still gates execution.
    private static final long LINK_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private static final Map<UUID, DynamicCommand> DYNAMIC_COMMANDS = new SelfExpiringMap<>(LINK_TTL_MILLIS);

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param dynamicCommand The dynamicCommand
     *
     * @return The Command link to this dynamicCommand, like "/${label} UUID"
     */
    public static synchronized String scheduleDynamicCommand(DynamicCommand dynamicCommand){
        DYNAMIC_COMMANDS.put(
                dynamicCommand.getUuid(),
                dynamicCommand
        );
        return "/ecdcmd " + dynamicCommand.getUuid().toString();
    }

    /** The link scheduled under {@code id}, or {@code null} when there is none (never made, expired, used up or its plugin left). */
    public static synchronized @Nullable DynamicCommand find(UUID id) {
        return DYNAMIC_COMMANDS.get(id);
    }

    public static synchronized void remove(UUID id) {
        DYNAMIC_COMMANDS.remove(id);
    }

    /** Internal: drops every link {@code owner} made - part of releasing that plugin, so no click runs a closure of a plugin that left. */
    public static synchronized void closeOwnedBy(ECPluginData owner) {
        DYNAMIC_COMMANDS.values().removeIf(command -> command.getOwner() == owner);
    }

}
