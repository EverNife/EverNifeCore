package br.com.finalcraft.evernifecore.dynamiccommand;

import br.com.finalcraft.evernifecore.consolefilter.ECBukkitConsoleFilter;

import java.util.HashMap;
import java.util.UUID;

public class DynamicCommandManager {

    public static HashMap<UUID, DynamicCommand>  DYNAMIC_COMMANDS = new HashMap<>();

    static {
        //Only if this class is ever used whe may register a filter for it on the console
        ECBukkitConsoleFilter.applyFilter();
    }

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param dynamicCommand The dynamicCommand
     *
     * @return The Command link to this dynamicCommand, like "/%label% UUID"
     */
    public static String scheduleDynamicCommand(DynamicCommand dynamicCommand){
        DYNAMIC_COMMANDS.put(
                dynamicCommand.getUuid(),
                dynamicCommand
        );
        return "/ecdcmd " + dynamicCommand.getUuid().toString();
    }

}
