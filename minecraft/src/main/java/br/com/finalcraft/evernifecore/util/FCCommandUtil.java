package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;

public class FCCommandUtil {


    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param runnable The runnable for this command
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
     * @param runnable The runnable for this command
     * @param maxTimeInSeconds The max amount of time this command can be run
     *
     * @return The Command link to this runnable, like "/%label% UUID"
     */
    public static String dynamicCommand(Runnable runnable, long maxTimeInSeconds){
        return dynamicCommand(runnable, maxTimeInSeconds, false);
    }

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param runnable The runnable for this command
     * @param maxTimeInSeconds The max amount of time this command can be run
     *
     * @return The Command link to this runnable, like "/%label% UUID"
     */
    public static String dynamicCommand(Runnable runnable, long maxTimeInSeconds, boolean runOnlyOnce){
        return DynamicCommand.builder()
                .setAction(context -> runnable.run())
                .setCooldown(maxTimeInSeconds)
                .setRunOnlyOnce(runOnlyOnce)
                .createDynamicCommand()
                .scheduleAndReturnCommandString();
    }
}
