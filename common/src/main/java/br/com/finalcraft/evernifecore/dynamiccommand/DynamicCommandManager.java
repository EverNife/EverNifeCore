package br.com.finalcraft.evernifecore.dynamiccommand;

import br.com.finalcraft.evernifecore.util.collection.SelfExpiringMap;

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

    public static Map<UUID, DynamicCommand> DYNAMIC_COMMANDS = new SelfExpiringMap<>(LINK_TTL_MILLIS);

    //TODO Register on EverNifeCore Miencraft Version
//    static {
//        //Only if this class is ever used whe may register a filter for it on the console
//        ECBukkitConsoleFilter.applyFilter();
//    }

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param dynamicCommand The dynamicCommand
     *
     * @return The Command link to this dynamicCommand, like "/${label} UUID"
     */
    public static String scheduleDynamicCommand(DynamicCommand dynamicCommand){
        DYNAMIC_COMMANDS.put(
                dynamicCommand.getUuid(),
                dynamicCommand
        );
        return "/ecdcmd " + dynamicCommand.getUuid().toString();
    }

}
