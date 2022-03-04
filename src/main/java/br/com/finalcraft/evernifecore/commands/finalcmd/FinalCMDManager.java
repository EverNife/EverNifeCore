package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.FCMultiLocales;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FinalCMDManager {

    public static boolean registerCommand(@NotNull JavaPlugin pluginInstance, @NotNull Class cmdClass) {
        try {
            Constructor constructor = cmdClass.getDeclaredConstructor();
            Object customExecutor = constructor.newInstance();
            return registerCommand(pluginInstance, customExecutor);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            pluginInstance.getLogger().warning("Fail to create instance of the FinalCMD Command: " + cmdClass.getName());
            pluginInstance.getLogger().warning("Does the class has a default constructor?");
            e.printStackTrace();
        }
        return false;
    }

    public static boolean registerCommand(@NotNull JavaPlugin pluginInstance, @NotNull Object executor) {
        try {
            List<Method> finalCMDMainMethods = new ArrayList<>();

            //Checking for all Methods that have a @FinalCMD
            for (Method declaredMethod : executor.getClass().getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(FinalCMD.class)){
                    finalCMDMainMethods.add(declaredMethod);
                }
            }

            //If there is no method with @FinalCMD annotation, maybe the class itself is annotated
            if (finalCMDMainMethods.size() == 0){
                if (executor.getClass().isAnnotationPresent(FinalCMD.class)){
                    finalCMDMainMethods.add(null);
                }else {
                    pluginInstance.getLogger().severe("Tried to register a FinalCMD(" + executor.getClass().getName() + ") without any @FinalCMD Annotation!");
                    return false;
                }
            }

            //Identify all LocaleMessages in this class and load it
            List<Field> localeMessageFields = new ArrayList<>();
            for (Field declaredField : executor.getClass().getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(FCLocale.class) || declaredField.isAnnotationPresent(FCMultiLocales.class)){
                    if (!Modifier.isStatic(declaredField.getModifiers())){
                        pluginInstance.getLogger().severe("The LocaleMessage [" + declaredField.getName() + "] found at [" + declaredField.getDeclaringClass().getName() + "] is not static! This is an error, it will be ignored!");
                    }else {
                        localeMessageFields.add(declaredField);
                    }
                }
            }

            //IF we have any method with FCLocale annotation, than load it using the FCLocaleManager
            if (localeMessageFields.size() > 0){
                FCLocaleManager.loadLocale(pluginInstance, executor.getClass());
            }

            if (finalCMDMainMethods.size() == 1){ //Check for SubCommands, maybe this @FinalCMD is in the Class
                Method mainCommandMethod = finalCMDMainMethods.get(0); //Element ZERO is null if we have a @FinalCMD annotation to the class rather than the function

                final FinalCMD finalCMD;
                if (mainCommandMethod != null){
                    finalCMD = mainCommandMethod.getAnnotation(FinalCMD.class);
                }else {
                    finalCMD = executor.getClass().getAnnotation(FinalCMD.class);
                }


                FinalCMDData finalCMDData = new FinalCMDData(finalCMD);
                List<Tuple<Method,SubCMDData>> subCMDsTuples = new ArrayList<>();

                for (Method declaredMethod : executor.getClass().getDeclaredMethods()) {
                    if (declaredMethod.isAnnotationPresent(FinalCMD.SubCMD.class)){
                        FinalCMD.SubCMD subCMD = declaredMethod.getAnnotation(FinalCMD.SubCMD.class);
                        SubCMDData subCMDData = new SubCMDData(subCMD);
                        subCMDsTuples.add(
                                Tuple.of(declaredMethod, subCMDData)
                        );
                    }
                }

                if (executor instanceof ICustomFinalCMD){
                    ((ICustomFinalCMD) executor).customize(finalCMDData,
                            ImmutableList.copyOf(
                                    subCMDsTuples.stream().map(Tuple::getBeta).collect(Collectors.toList())
                            )
                    );
                }

                CMDMethodInterpreter mainMethodInterpreter = (mainCommandMethod == null ? null : new CMDMethodInterpreter(pluginInstance, mainCommandMethod, executor, finalCMDData));

                FinalCMDPluginCommand newCommand = new FinalCMDPluginCommand(pluginInstance, finalCMDData, mainMethodInterpreter);
                for (Tuple<Method, SubCMDData> tuple : subCMDsTuples) {
                    CMDMethodInterpreter subCommandInterpreter = new CMDMethodInterpreter(pluginInstance, tuple.getAlfa(), executor, tuple.getBeta());
                    newCommand.addSubCommand(subCommandInterpreter);
                }

                newCommand.addLocaleMessages(localeMessageFields);
                newCommand.registerCommand();
                return true;
            }

            // We have several @FinalCMD annotated methods on this class, lets register all of them.
            // Each one is a different command without any SubCommand
            for (Method mainCommandMethod : finalCMDMainMethods) {
                try {
                    FinalCMD finalCMD = mainCommandMethod.getAnnotation(FinalCMD.class);

                    FinalCMDData finalCMDData = new FinalCMDData(finalCMD);

                    if (executor instanceof ICustomFinalCMD){
                        ((ICustomFinalCMD) executor).customize(finalCMDData, Collections.EMPTY_LIST);
                    }

                    CMDMethodInterpreter mainMethodInterpreter = mainCommandMethod == null ? null : new CMDMethodInterpreter(pluginInstance, mainCommandMethod, executor, finalCMDData);

                    FinalCMDPluginCommand newCommand = new FinalCMDPluginCommand(pluginInstance, finalCMDData, mainMethodInterpreter);

                    newCommand.addLocaleMessages(localeMessageFields);
                    newCommand.registerCommand();
                }catch (Throwable e){
                    pluginInstance.getLogger().severe("Error registering a FinalCMD on the class [" + executor.getClass().getName() + "] method " + mainCommandMethod.getName() + "!");
                    e.printStackTrace();
                }
            }

            //We are in a case where there are several @FinalCMD methods, we cannot allow SubCMDs in this class
            // lets check for it just to warn the developer
            for (Method declaredMethod : executor.getClass().getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD.class)){
                    pluginInstance.getLogger().severe("Found a SubCMD on the class [" + executor.getClass().getName() + "] method " + declaredMethod.getName() + " but the class has more than one FinalCMD, this will be ignored!");
                }
            }

            return true;
        }catch (Throwable e){
            pluginInstance.getLogger().warning("Fail to register FinalCMD Command: " + executor.getClass().getName());
            e.printStackTrace();
        }
        return false;
    }

    public static void unregisterCommand(String commandName){
        unregisterCommand(commandName, EverNifeCore.instance);
    }

    public static void unregisterCommand(String commandName, Plugin notifyPlugin){
        try {
            Map<String, Command> mapOfCommands = getCommandMapKnownCommands();
            Command existingCommand = mapOfCommands.get(commandName);
            if (existingCommand == null){
                return; //Command is not registered
            }

            mapOfCommands.remove(commandName);

            String originalPlugin = "BUKKIT";
            if (existingCommand instanceof PluginIdentifiableCommand){
                Plugin plugin = ((PluginIdentifiableCommand) existingCommand).getPlugin();
                if (plugin != null){
                    originalPlugin = " Plugin: " + plugin.getName();
                }
            }

            if (commandName.equals(existingCommand.getName())){
                notifyPlugin.getLogger().warning("Removing existent command [" + existingCommand.getName() + "] from " + originalPlugin + "!");
            }else {
                notifyPlugin.getLogger().warning("Removing existent alias (" + commandName + ") for [" + existingCommand.getName() + "] from " + originalPlugin + "!");
            }
        }catch (Exception e){
            EverNifeCore.warning("Failed to UNREGISTER command [" +  commandName + "]");
            e.printStackTrace();
        }
    }

    public static void createAlias(String alias, String theCommand){

    }

    private static CommandMap commandMap = null;
    public static CommandMap getCommandMap(){
        if (commandMap == null){
            try {
                Field field = SimplePluginManager.class.getDeclaredField("commandMap");
                field.setAccessible(true);
                commandMap = (CommandMap)(field.get(Bukkit.getServer().getPluginManager()));
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return commandMap;
    }

    public static Map<String, Command> getCommandMapKnownCommands(){
        CommandMap commandMap = getCommandMap();
        try {
            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            Map<String, Command> knowCommands = (Map<String, Command>) field.get(commandMap);
            return knowCommands;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
