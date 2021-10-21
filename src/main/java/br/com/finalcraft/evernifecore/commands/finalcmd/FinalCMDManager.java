package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.FCMultiLocales;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
            List<Method> finalCMDMethods = new ArrayList<>();
            for (Method declaredMethod : executor.getClass().getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.class)){
                    finalCMDMethods.add(declaredMethod);
                }
            }

            if (finalCMDMethods.size() == 0){
                if (executor.getClass().isAnnotationPresent(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.class)){
                    finalCMDMethods.add(null);
                }else {
                    pluginInstance.getLogger().warning("Tried to register a FinalCMD(" + executor.getClass().getName() + ") without any @FinalCMD annotation? Is this right?");
                    return false;
                }
            }

            List<Field> localeMessageFields = new ArrayList<>();
            for (Field declaredField : executor.getClass().getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(FCLocale.class) || declaredField.isAnnotationPresent(FCMultiLocales.class)){
                    if (Modifier.isStatic(declaredField.getModifiers())){
                        pluginInstance.getLogger().warning("LocaleMessage [" + declaredField.getName() + "] found at [" + declaredField.getDeclaringClass().getName() + "] is not static! This is an error!");
                    }else {
                        localeMessageFields.add(declaredField);
                    }
                }
            }
            if (localeMessageFields.size() > 0){
                FCLocaleManager.loadLocale(pluginInstance, executor.getClass());
            }

            if (finalCMDMethods.size() == 1){ //Check for SubCommands, maybe this @FinalCMD is in the Class
                Method builderMethod = finalCMDMethods.get(0);
                br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMD;
                if (builderMethod != null){
                    finalCMD = builderMethod.getAnnotation(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.class);
                }else {
                    finalCMD = executor.getClass().getAnnotation(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.class);
                }

                if (finalCMD.aliases().length < 1) {
                    pluginInstance.getLogger().warning("FinalCMD annotation without at least one aliase! This is an error!");
                    pluginInstance.getLogger().warning("[" + executor.getClass().getName() + "] method: " + builderMethod == null ? "Class Method" : builderMethod.getName());
                    return false;
                }

                FinalCMDPluginCommand newCommand =
                        new FinalCMDPluginCommand(pluginInstance, finalCMD.aliases()[0])
                                .setAliases(finalCMD.aliases())
                                .setUsage(finalCMD)
                                .setDescription(finalCMD.desc().isEmpty() ? pluginInstance.getName() + "'s Command!" : finalCMD.desc());
                ;

                if (builderMethod != null){
                    newCommand.setExecutor(builderMethod, executor, finalCMD);
                }else {
                    newCommand.setExecutor(finalCMD);
                }

                for (Method declaredMethod : executor.getClass().getDeclaredMethods()) {
                    if (declaredMethod.isAnnotationPresent(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD.class)){
                        br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD subCMD = declaredMethod.getAnnotation(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD.class);
                        newCommand.addSubCommand(declaredMethod, executor, subCMD);
                    }
                }

                newCommand.addLocaleMessages(localeMessageFields);
                newCommand.registerCommand();
                return true;
            }

            int errored = 0;
            for (Method builderMethod : finalCMDMethods) {
                br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMD = builderMethod.getAnnotation(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.class);

                if (finalCMD.aliases().length < 1) {
                    pluginInstance.getLogger().warning("FinalCMD annotation without at least one aliase! Skipping it!");
                    pluginInstance.getLogger().warning("[" + executor.getClass().getName() + "] method: " + builderMethod.getName());
                    errored++;
                    continue;
                }

                FinalCMDPluginCommand newCommand =
                        new FinalCMDPluginCommand(pluginInstance, finalCMD.aliases()[0])
                                .setAliases(finalCMD.aliases())
                                .setUsage(finalCMD)
                                .setDescription(finalCMD.desc().isEmpty() ? pluginInstance.getName() + "'s Command!" : finalCMD.desc())
                                .setExecutor(builderMethod, executor, finalCMD);
                ;

                newCommand.addLocaleMessages(localeMessageFields);
                newCommand.registerCommand();
            }

            for (Method declaredMethod : executor.getClass().getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD.class)){
                    pluginInstance.getLogger().warning("Found a SubCMD on class [" + executor.getClass().getName() + "] method " + declaredMethod.getName() + " but the class has more than one FinalCMD, this will be ignored!");
                }
            }

            if (errored > 0){
                if (errored == finalCMDMethods.size()){
                    pluginInstance.getLogger().warning("Tried to register a FinalCMD(" + executor.getClass().getName() + ") but all of its @FinalCMD annotation seems to have no aliase!");
                    return false;
                }else {
                    pluginInstance.getLogger().warning(errored + " out of " + finalCMDMethods.size() + " commands registered!");
                }
            }
            return true;
        }catch (Throwable e){
            pluginInstance.getLogger().warning("Fail to register FinalCMD Command: " + executor.getClass().getName());
            e.printStackTrace();
        }
        return false;
    }

    public static boolean registerCommand(@NotNull JavaPlugin pluginInstance, @NotNull String name, @NotNull FinalCMDExecutor finalCMDExecutor) {
        try {
            if (!finalCMDExecutor.shouldRegister()){
                return false;
            }

            List<Field> localeMessageFields = new ArrayList<>();
            for (Field declaredField : finalCMDExecutor.getClass().getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(FCLocale.class) || declaredField.isAnnotationPresent(FCMultiLocales.class)){
                    localeMessageFields.add(declaredField);
                }
            }
            if (localeMessageFields.size() > 0){
                FCLocaleManager.loadLocale(pluginInstance, finalCMDExecutor.getClass());
            }

            FinalCMDBuilder finalCmdBuilder = finalCMDExecutor.getFinalCmdBuilder();

            Method onCommandMethod = finalCMDExecutor.getClass().getDeclaredMethod("onCommand", CommandSender.class, String.class, MultiArgumentos.class);
            if (onCommandMethod.isAnnotationPresent(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDBuilder.class)){
                br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDBuilder cmdAnnotation = onCommandMethod.getAnnotation(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDBuilder.class);
                finalCmdBuilder.flagByDefault = cmdAnnotation.flagArgs();
                if (cmdAnnotation.aliases() != null && !cmdAnnotation.aliases()[0].isEmpty()){
                    finalCmdBuilder.setAliases(cmdAnnotation.aliases());
                }
            }

            if (finalCmdBuilder.javaPlugin == null) finalCmdBuilder.javaPlugin = pluginInstance;

            finalCmdBuilder.name = name;

            List<String> aliases = new ArrayList<>(finalCmdBuilder.aliases);
            aliases.removeIf(aliase -> aliase.equalsIgnoreCase(name));
            finalCmdBuilder.aliases = Collections.unmodifiableList(aliases);

            FinalCMDPluginCommand newCommand =
                    new FinalCMDPluginCommand(pluginInstance,name)
                            .setUsage("/" + finalCmdBuilder.name)
                            .setAliases(finalCmdBuilder.aliases.toArray(new String[0]))
                            .setDescription(finalCmdBuilder.javaPlugin.getName() + "'s Command!")
                            .setExecutor(finalCMDExecutor)
                    ;

            if (!finalCmdBuilder.tabComplete.isEmpty()){
                newCommand.tabComplete = finalCmdBuilder.tabComplete;
            }

            newCommand.addLocaleMessages(localeMessageFields);
            newCommand.registerCommand();
            return true;
        }catch (Throwable e){
            pluginInstance.getLogger().warning("Fail to register FinalCMD Command: " + name);
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
            if (existingCommand != null){

                mapOfCommands.remove(commandName);

                String originalPlugin = "BUKKIT";
                if (existingCommand instanceof PluginIdentifiableCommand){
                    Plugin plugin = ((PluginIdentifiableCommand) existingCommand).getPlugin();
                    if (plugin != null){
                        originalPlugin = " from plugin " + plugin.getName();
                    }
                }

                notifyPlugin.getLogger().warning("Removing existent command [" + existingCommand.getName() + "] from " + originalPlugin + "!");
            }
        }catch (Exception e){
            EverNifeCore.warning("Failed to UNREGISTER command [" +  commandName + "]");
            e.printStackTrace();
        }
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
