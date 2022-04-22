package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommandManager;

public class FCCommandUtil {


    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param runnable The runnable of to this command
     *
     * @return The Command link to this runnable, like "/%label% UUID"
     */
    public static String dynamicCommand(Runnable runnable){
        return dynamicCommand(runnable, 1200);
    }

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param runnable The runnable of to this command
     * @param maxTimeInSeconds The max amount of time this command can be run
     *
     * @return The Command link to this runnable, like "/%label% UUID"
     */
    public static String dynamicCommand(Runnable runnable, long maxTimeInSeconds){
        return DynamicCommandManager.scheduleDynamicCommand(
                DynamicCommand.builder()
                        .setAction(context -> runnable.run())
                        .setCooldown(maxTimeInSeconds)
                        .createDynamicCommand()
        );
    }

    /**
     * Creates a Command String that will execute this dynamicCommand when the
     * command is executed by the player
     *
     * @param dynamicCommand The dynamicCommand
     *
     * @return The Command link to this dynamicCommand, like "/%label% UUID"
     */
    public static String dynamicCommand(DynamicCommand dynamicCommand){
        return DynamicCommandManager.scheduleDynamicCommand(dynamicCommand);
    }
}
