package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandTreeScanner;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.AccountSection;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.pageviewer.PageVisualization;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import jakarta.annotation.Nonnull;

import java.lang.reflect.*;
import java.util.*;

public class FinalCMDManager {

    private static boolean builtinParsersRegistered = false;

    static {
        registerBuiltinParsers();
    }

    /**
     * Registers the {@code ArgParser}s every ECPlugin gets for free, plus the platform's own. Runs
     * from this class's static initializer - which every registration path touches - and is idempotent,
     * so a caller that needs the framework ready WITHOUT registering a command may call it directly.
     */
    public static synchronized void registerBuiltinParsers() {
        if (builtinParsersRegistered){
            return;
        }
        builtinParsersRegistered = true;

        //Needs to be registered here because we need them for plugins that load before EverNifeCore
        ArgParserManager.addBuiltinParser(Argumento.class, ArgParserArgumento.class);
        ArgParserManager.addBuiltinParser(String.class, ArgParserString.class);
        //Every numeric wrapper the parser can hand back as itself - a type it cannot narrow to would
        //convert fine here and blow up inside method.invoke instead
        ArgParserManager.addBuiltinParser(Integer.class, ArgParserNumber.class);
        ArgParserManager.addBuiltinParser(Long.class, ArgParserNumber.class);
        ArgParserManager.addBuiltinParser(Short.class, ArgParserNumber.class);
        ArgParserManager.addBuiltinParser(Byte.class, ArgParserNumber.class);
        ArgParserManager.addBuiltinParser(Float.class, ArgParserNumber.class);
        ArgParserManager.addBuiltinParser(Double.class, ArgParserNumber.class);
        ArgParserManager.addBuiltinParser(NumberWrapper.class, ArgParserNumberWrapper.class);
        ArgParserManager.addBuiltinParser(IPlayerData.class, ArgParserIPlayerData.class);
        ArgParserManager.addBuiltinParser(AccountSection.class, ArgParserAccountSection.class);
        ArgParserManager.addBuiltinParser(Boolean.class, ArgParserBoolean.class);
        ArgParserManager.addBuiltinParser(Enum.class, ArgParserEnum.class);
        ArgParserManager.addBuiltinParser(UUID.class, ArgParserUUID.class);
        ArgParserManager.addBuiltinParser(PageVisualization.class, ArgParserPageVisualization.class);
        ArgParserManager.addBuiltinParser(FCTimeFrame.class, ArgParserFCTimeFrame.class);
        ArgParserManager.addBuiltinParser(FPlayer.class, ArgParserFPlayer.class);

        ArgParserManager.addBuiltinContextualParser(FPlayer.class, ArgParserContextualFPlayer.class);
        ArgParserManager.addBuiltinContextualParser(FCommandSender.class, ArgParserContextualFCommandSender.class);
        ArgParserManager.addBuiltinContextualParser(HelpContext.class, ArgParserContextualHelpContext.class);
        ArgParserManager.addBuiltinContextualParser(HelpLine.class, ArgParserContextualHelpLine.class);
        ArgParserManager.addBuiltinContextualParser(CommandPath.class, ArgParserContextualCommandPath.class);
        ArgParserManager.addBuiltinContextualParser(String.class, ArgParserContextualLabel.class);
        ArgParserManager.addBuiltinContextualParser(MultiArgumentos.class, ArgParserContextualMultiArgumentos.class);
        ArgParserManager.addBuiltinContextualParser(PDSection.class, ArgParserContextualPDSection.class);
        ArgParserManager.addBuiltinContextualParser(AccountSection.class, ArgParserContextualAccountSection.class);
        ArgParserManager.addBuiltinContextualParser(PlayerData.class, ArgParserContextualPlayerData.class);

        EverNifeCore.getPlatform().registerArgParsers();
    }

    /**
     * Registers every {@code @FinalCMD} found on {@code cmdClass} (instantiated through its no-arg
     * constructor) - see {@link #registerCommand(ECPluginData, Object)} for the full contract.
     *
     * @return every {@link FinalCMDPluginCommand} that was actually registered; empty on total failure
     */
    public static List<FinalCMDPluginCommand> registerCommand(@Nonnull ECPluginData ecPluginData, @Nonnull Class<?> cmdClass) {
        try {
            Constructor constructor = cmdClass.getDeclaredConstructor();
            Object customExecutor = constructor.newInstance();
            return registerCommand(ecPluginData, customExecutor);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            ecPluginData.getLog().severe("Fail to create instance of the FinalCMD Command: " + cmdClass.getName()
                    + " - does the class have a no-arg constructor?", e);
        }
        return Collections.emptyList();
    }

    /**
     * Scans {@code executor} for {@code @FinalCMD} methods (or a class-level annotation) and registers
     * each one found. A class with a SINGLE {@code @FinalCMD} may declare {@code @SubCMD} methods
     * alongside it (one command, several subcommands); a class with SEVERAL independent
     * {@code @FinalCMD} methods registers each as its own command (subcommands are not allowed there).
     * <p>
     * Nothing thrown here escapes: a command whose shape the framework refuses is logged and lost, the
     * server still opens. To read what such a refusal teaches, scan without registering through
     * {@link CommandTreeScanner#scanCommands(ECPluginData, Object)}.
     *
     * @return every {@link FinalCMDPluginCommand} actually registered, in registration order, or empty
     *         when nothing succeeded; a multi-{@code @FinalCMD} class may return a PARTIAL list
     */
    public static List<FinalCMDPluginCommand> registerCommand(@Nonnull ECPluginData ecPluginData, @Nonnull Object executor) {
        try {
            List<CommandTreeScanner.ScannedCommand> scannedCommands = CommandTreeScanner.scanCommands(ecPluginData, executor);
            if (scannedCommands.isEmpty()){
                return Collections.emptyList();
            }

            List<FinalCMDPluginCommand> registeredCommands = new ArrayList<>();
            for (CommandTreeScanner.ScannedCommand scannedCommand : scannedCommands) {
                FinalCMDPluginCommand newCommand = new FinalCMDPluginCommand(ecPluginData, scannedCommand.getFinalCMDData(), scannedCommand.getRoot());
                if (newCommand.registerCommand()){
                    registeredCommands.add(newCommand);
                }
            }

            ECPluginManager.getOrCreateECorePluginData(ecPluginData).reloadAllCustomLocales();
            return registeredCommands;
        }catch (Throwable e){
            //A malformed command is lost, with the reason in the log - the same deal every other
            //registration in this project makes, listeners included. The boot never stops for it.
            ecPluginData.getLog().severe("Fail to register FinalCMD Command: " + executor.getClass().getName(), e);
        }
        return Collections.emptyList();
    }

    public static void unregisterCommand(String commandName){
        unregisterCommand(commandName, EverNifeCore.instance.getEcPluginData());
    }

    public static void unregisterCommand(String commandName, ECPluginData notifyPlugin){
        EverNifeCore.getPlatform().unregisterCommand(commandName, notifyPlugin);
    }

    /**
     * Unregisters every command {@code ecPluginData} has tracked as registered (iterates a copy - see
     * {@link ECPluginData#getRegisteredCommands()} - so each {@link FinalCMDPluginCommand#unregister()}
     * call mutating the live list is safe).
     */
    public static void unregisterAllCommands(@Nonnull ECPluginData ecPluginData) {
        for (FinalCMDPluginCommand command : ecPluginData.getRegisteredCommands()) {
            command.unregister();
        }
    }

}
