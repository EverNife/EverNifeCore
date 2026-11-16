package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nonnull;

public class FCCommandUtil {


    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param owner The plugin making the link; the link stops working when it leaves
     * @param runnable The runnable for this command
     *
     * @return The Command link to this runnable, like "/${label} UUID"
     */
    public static String dynamicCommand(@Nonnull ECPluginData owner, Runnable runnable){
        return dynamicCommand(owner, runnable, 1200);
    }

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param owner The plugin making the link; the link stops working when it leaves
     * @param runnable The runnable for this command
     * @param maxTimeInSeconds The max amount of time this command can be run
     *
     * @return The Command link to this runnable, like "/${label} UUID"
     */
    public static String dynamicCommand(@Nonnull ECPluginData owner, Runnable runnable, long maxTimeInSeconds){
        return dynamicCommand(owner, runnable, maxTimeInSeconds, false);
    }

    /**
     * Creates a Command String that will execute this runnable when the command
     * is executed by the player
     *
     * @param owner The plugin making the link; the link stops working when it leaves
     * @param runnable The runnable for this command
     * @param maxTimeInSeconds The max amount of time this command can be run
     *
     * @return The Command link to this runnable, like "/${label} UUID"
     */
    public static String dynamicCommand(@Nonnull ECPluginData owner, Runnable runnable, long maxTimeInSeconds, boolean runOnlyOnce){
        return DynamicCommand.builder(owner)
                .setAction(context -> runnable.run())
                .setCooldown(maxTimeInSeconds)
                .setRunOnlyOnce(runOnlyOnce)
                .createDynamicCommand()
                .scheduleAndReturnCommandString();
    }

}
