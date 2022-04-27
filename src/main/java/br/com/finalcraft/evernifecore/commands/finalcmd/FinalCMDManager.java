package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CustomizeContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.MethodData;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.FCMultiLocales;
import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

public class FinalCMDManager {

    static {
        //The ArgParsers bellow will be available to all ECPlugins
        //Needs to be registered here because we need them for plugins that load before EverNifeCore
        ArgParserManager.addGlobalParser(String.class, ArgParserString.class);
        ArgParserManager.addGlobalParser(Integer.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Float.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Double.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Player.class, ArgParserPlayer.class);
        ArgParserManager.addGlobalParser(IPlayerData.class, ArgParserIPlayerData.class);
        ArgParserManager.addGlobalParser(Boolean.class, ArgParserBoolean.class);
        ArgParserManager.addGlobalParser(UUID.class, ArgParserUUID.class);
        ArgParserManager.addGlobalParser(World.class, ArgParserWorld.class);
    }

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
            List<Tuple<FinalCMD, Method>> finalCMDMainMethods = new ArrayList<>();

            //Add all declared-methods from the class and its supper-classes until Object
            HashSet<Method> methods = new HashSet<>();
            Class father = executor.getClass();
            while (father != null && father != Object.class){
                methods.addAll(Arrays.asList(father.getDeclaredMethods()));
                father = father.getSuperclass();
            }

            //Checking for all methods that have a @FinalCMD
            for (Method declaredMethod : methods) {
                FinalCMD finalCMD = ReflectionUtil.getAnnotationDeeply(declaredMethod, FinalCMD.class);
                if (finalCMD != null){
                    finalCMDMainMethods.add(Tuple.of(finalCMD, declaredMethod));
                }
            }

            //If there is no method with @FinalCMD annotation, maybe the class itself is annotated
            if (finalCMDMainMethods.size() == 0){
                FinalCMD finalCMD = ReflectionUtil.getAnnotationDeeply(executor.getClass(), FinalCMD.class);
                if (finalCMD == null){
                    pluginInstance.getLogger().severe("Tried to register a FinalCMD(" + executor.getClass().getName() + ") without any @FinalCMD Annotation!");
                    return false;
                }
                finalCMDMainMethods.add(Tuple.of(finalCMD, null));
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

            //IF we have any method with FCLocale annotation, then load it using the FCLocaleManager
            if (localeMessageFields.size() > 0){
                FCLocaleManager.loadLocale(pluginInstance, executor.getClass());
            }

            Collections.sort(localeMessageFields, Comparator.comparing(Field::getName)); //Sort LocaleMessage fields by its name

            if (finalCMDMainMethods.size() == 1){ //Check for SubCommands, maybe this @FinalCMD is in the Class
                Tuple<FinalCMD, Method> tuple = finalCMDMainMethods.get(0);

                final FinalCMD finalCMD = tuple.getAlfa();
                @Nullable Method mainCommandMethod = tuple.getBeta(); //Method is null if we have a @FinalCMD annotation to the class rather than the function

                FinalCMDData finalCMDData = new FinalCMDData(finalCMD);
                MethodData<FinalCMDData> mainMethodData = new MethodData(finalCMDData, mainCommandMethod);

                List<MethodData<SubCMDData>> subCommandsMethodData = new ArrayList<>();
                for (Method declaredMethod : methods) {
                    FinalCMD.SubCMD subCMD = ReflectionUtil.getAnnotationDeeply(declaredMethod, FinalCMD.SubCMD.class);
                    if (subCMD != null){
                        SubCMDData subCMDData = new SubCMDData(subCMD);
                        subCommandsMethodData.add(new MethodData(subCMDData, declaredMethod));
                    }
                }

                CustomizeContext customizeContext = new CustomizeContext(mainMethodData, subCommandsMethodData);

                if (executor instanceof ICustomFinalCMD){
                    //Apply command customization if necessary
                    ((ICustomFinalCMD) executor).customize(customizeContext);
                }

                CMDMethodInterpreter mainMethodInterpreter = (mainCommandMethod == null ? null : new CMDMethodInterpreter(pluginInstance, customizeContext.getMainMethod(), executor));

                FinalCMDPluginCommand newCommand = new FinalCMDPluginCommand(pluginInstance, finalCMDData, mainMethodInterpreter);
                for (MethodData<SubCMDData> subCMDDataMethodData : customizeContext.getSubMethods()) {
                    CMDMethodInterpreter subCommandInterpreter = new CMDMethodInterpreter(pluginInstance, subCMDDataMethodData, executor);
                    newCommand.addSubCommand(subCommandInterpreter);
                }

                newCommand.addLocaleMessages(localeMessageFields);
                newCommand.registerCommand();
                ECPluginManager.getOrCreateECorePluginData(pluginInstance).reloadAllCustomLocales();
                return true;
            }

            // We have several @FinalCMD annotated methods on this class, lets register all of them.
            // Each one is a different command without any SubCommand
            for (Tuple<FinalCMD, Method> tuple : finalCMDMainMethods) {
                try {
                    FinalCMD finalCMD = tuple.getAlfa();
                    Method mainCommandMethod = tuple.getBeta();

                    FinalCMDData finalCMDData = new FinalCMDData(finalCMD);
                    MethodData<FinalCMDData> mainMethodData = new MethodData(finalCMDData, mainCommandMethod);
                    CustomizeContext customizeContext = new CustomizeContext(mainMethodData, Collections.EMPTY_LIST);

                    if (executor instanceof ICustomFinalCMD){
                        //Apply command customization if necessary
                        ((ICustomFinalCMD) executor).customize(customizeContext);
                    }

                    CMDMethodInterpreter mainMethodInterpreter = mainCommandMethod == null ? null : new CMDMethodInterpreter(pluginInstance, customizeContext.getMainMethod(), executor);

                    FinalCMDPluginCommand newCommand = new FinalCMDPluginCommand(pluginInstance, finalCMDData, mainMethodInterpreter);

                    newCommand.addLocaleMessages(localeMessageFields);
                    newCommand.registerCommand();
                }catch (Throwable e){
                    pluginInstance.getLogger().severe("Error registering a FinalCMD on the class [" + executor.getClass().getName() + "] method " + tuple.getBeta().getName() + "!");
                    e.printStackTrace();
                }
            }

            //We are in a case where there are several @FinalCMD methods, we cannot allow SubCMDs in this class
            // lets check for it just to warn the developer
            for (Method declaredMethod : methods) {
                if (declaredMethod.isAnnotationPresent(FinalCMD.SubCMD.class)){
                    pluginInstance.getLogger().severe("Found a SubCMD on the class [" + executor.getClass().getName() + "] method " + declaredMethod.getName() + " but the class has more than one FinalCMD, this will be ignored!");
                }
            }

            ECPluginManager.getOrCreateECorePluginData(pluginInstance).reloadAllCustomLocales();
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
                    originalPlugin = "Plugin: " + plugin.getName();
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
