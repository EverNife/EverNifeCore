package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFPlayer;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.hytale.ArgParserFPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.hytale.ArgParserPlayerRef;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.MethodData;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.FCMultiLocales;
import br.com.finalcraft.evernifecore.pageviwer.PageVizualization;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCJavaPluginUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.*;
import java.util.*;

public class FinalCMDManager {

    static {
        //The ArgParsers bellow will be available to all ECPlugins
        //Needs to be registered here because we need them for plugins that load before EverNifeCore
        ArgParserManager.addGlobalParser(Argumento.class, ArgParserArgumento.class);
        ArgParserManager.addGlobalParser(String.class, ArgParserString.class);
        ArgParserManager.addGlobalParser(Integer.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Float.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Double.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(NumberWrapper.class, ArgParserNumberWrapper.class);
        ArgParserManager.addGlobalParser(IPlayerData.class, ArgParserIPlayerData.class);
        ArgParserManager.addGlobalParser(Boolean.class, ArgParserBoolean.class);
        ArgParserManager.addGlobalParser(Enum.class, ArgParserEnum.class);
        ArgParserManager.addGlobalParser(UUID.class, ArgParserUUID.class);
        ArgParserManager.addGlobalParser(World.class, ArgParserWorld.class);
        ArgParserManager.addGlobalParser(PageVizualization.class, ArgParserPageVizualization.class);
        ArgParserManager.addGlobalParser(FCTimeFrame.class, ArgParserFCTimeFrame.class);

        //Hytale Specific
        ArgParserManager.addGlobalParser(PlayerRef.class, ArgParserPlayerRef.class);
        ArgParserManager.addGlobalParser(FPlayer.class, ArgParserFPlayer.class);
        ArgParserManager.addGlobalParser(HytaleFPlayer.class, ArgParserFPlayer.class);

    }

    public static boolean registerCommand(@Nonnull JavaPlugin pluginInstance, @Nonnull Class cmdClass) {
        try {
            Constructor constructor = cmdClass.getDeclaredConstructor();
            Object customExecutor = constructor.newInstance();
            return registerCommand(pluginInstance, customExecutor);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            pluginInstance.getLogger().atWarning().log("Fail to create instance of the FinalCMD Command: " + cmdClass.getName());
            pluginInstance.getLogger().atWarning().log("Does the class has a default constructor?");
            e.printStackTrace();
        }
        return false;
    }

    public static boolean registerCommand(@Nonnull JavaPlugin pluginInstance, @Nonnull Object executor) {
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
                if (declaredMethod.isAnnotationPresent(FinalCMD.Ignore.class)){
                    continue;
                }

                FinalCMD finalCMD = FCReflectionUtil.getAnnotationDeeply(declaredMethod, FinalCMD.class);
                if (finalCMD != null){
                    finalCMDMainMethods.add(Tuple.of(finalCMD, declaredMethod));
                }
            }

            //If there is no method with @FinalCMD annotation, maybe the class itself is annotated
            if (finalCMDMainMethods.size() == 0){
                FinalCMD finalCMD = FCReflectionUtil.getAnnotationDeeply(executor.getClass(), FinalCMD.class);
                if (finalCMD == null){
                    pluginInstance.getLogger().atSevere().log("Tried to register a FinalCMD(" + executor.getClass().getName() + ") without any @FinalCMD Annotation!");
                    return false;
                }
                finalCMDMainMethods.add(Tuple.of(finalCMD, null));
            }

            //Identify all LocaleMessages in this class and load it
            List<Field> localeMessageFields = new ArrayList<>();
            for (Field declaredField : executor.getClass().getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(FCLocale.class) || declaredField.isAnnotationPresent(FCMultiLocales.class)){
                    if (!Modifier.isStatic(declaredField.getModifiers())){
                        pluginInstance.getLogger().atSevere().log("The LocaleMessage [" + declaredField.getName() + "] found at [" + declaredField.getDeclaringClass().getName() + "] is not static! This is an error, it will be ignored!");
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

                final FinalCMD finalCMD = tuple.getLeft();
                @Nullable Method mainCommandMethod = tuple.getRight(); //Method is null if we have a @FinalCMD annotation to the class rather than the function

                FinalCMDData finalCMDData = new FinalCMDData(finalCMD);
                MethodData<FinalCMDData> mainMethodData = new MethodData(finalCMDData, mainCommandMethod);

                List<MethodData<SubCMDData>> subCommandsMethodData = new ArrayList<>();
                for (Method declaredMethod : methods) {
                    FinalCMD.SubCMD subCMD = FCReflectionUtil.getAnnotationDeeply(declaredMethod, FinalCMD.SubCMD.class);
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

                //After customization, lets load the Validators locales
                for (CMDData<?> cmdData : customizeContext.getAllCMDData()) {
                    //If it's not the default validator, lets load its locale
                    for (CMDAccessValidation cmdAccessValidation : cmdData.getCmdAccessValidations()) {
                        Class validationClass = cmdAccessValidation.getClass();
                        //Maybe the Validation class is not from this ECPlugin, so lets make sure its loaded on its proper owner
                        JavaPlugin plugin = FCJavaPluginUtil.getProvidingPlugin(validationClass);
                        FCLocaleManager.loadLocale(plugin, true, validationClass);
                    }
                }

                CMDMethodInterpreter mainMethodInterpreter = mainCommandMethod == null
                        ? null :
                        new CMDMethodInterpreter(pluginInstance, customizeContext.getMainMethod(), executor);

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
                    FinalCMD finalCMD = tuple.getLeft();
                    Method mainCommandMethod = tuple.getRight();

                    FinalCMDData finalCMDData = new FinalCMDData(finalCMD);
                    MethodData<FinalCMDData> mainMethodData = new MethodData(finalCMDData, mainCommandMethod);
                    CustomizeContext customizeContext = new CustomizeContext(mainMethodData, Collections.EMPTY_LIST);

                    if (executor instanceof ICustomFinalCMD){
                        //Apply command customization if necessary
                        ((ICustomFinalCMD) executor).customize(customizeContext);
                    }

                    //After customization, lets load the Validators locales
                    for (CMDData<?> cmdData : customizeContext.getAllCMDData()) {
                        //If its not the default validator, lets load its locale
                        Class validationClass = cmdData.getCmdAccessValidations().getClass();
                        //Maybe the Validation class is not from this ECPlugin, so lets make sure its loaded on its proper owner
                        JavaPlugin plugin = FCJavaPluginUtil.getProvidingPlugin(validationClass);
                        FCLocaleManager.loadLocale(plugin, true, validationClass);
                    }

                    CMDMethodInterpreter mainMethodInterpreter = mainCommandMethod == null ? null : new CMDMethodInterpreter(pluginInstance, customizeContext.getMainMethod(), executor);

                    FinalCMDPluginCommand newCommand = new FinalCMDPluginCommand(pluginInstance, finalCMDData, mainMethodInterpreter);

                    newCommand.addLocaleMessages(localeMessageFields);
                    newCommand.registerCommand();
                }catch (Throwable e){
                    pluginInstance.getLogger().atSevere().log("Error registering a FinalCMD on the class [" + executor.getClass().getName() + "] method " + tuple.getRight().getName() + "!");
                    e.printStackTrace();
                }
            }

            //We are in a case where there are several @FinalCMD methods, we cannot allow SubCMDs in this class
            // lets check for it just to warn the developer
            for (Method declaredMethod : methods) {
                if (declaredMethod.isAnnotationPresent(FinalCMD.SubCMD.class)){
                    pluginInstance.getLogger().atSevere().log("Found a SubCMD on the class [" + executor.getClass().getName() + "] method " + declaredMethod.getName() + " but the class has more than one FinalCMD, this will be ignored!");
                }
            }

            ECPluginManager.getOrCreateECorePluginData(pluginInstance).reloadAllCustomLocales();
            return true;
        }catch (Throwable e){
            pluginInstance.getLogger().atWarning().log("Fail to register FinalCMD Command: " + executor.getClass().getName());
            e.printStackTrace();
        }
        return false;
    }

    public static void unregisterCommand(String commandName){
        unregisterCommand(commandName, EverNifeCore.instance);
    }

    public static void unregisterCommand(String commandName, JavaPlugin notifyPlugin){
        try {
            for (AbstractCommand existingCommand : CommandManager.get().getCommandRegistration().values()) {
                if (existingCommand.getName().equalsIgnoreCase(commandName)){
                    CommandManager.get().getCommandRegistration().remove(commandName);
                    notifyPlugin.getLogger().atWarning().log("Removing existent command [" + commandName + "] from " + existingCommand.getOwner().getName() + "!");
                    return;
                }

                if (existingCommand.getAliases().contains(commandName)){
                    existingCommand.getAliases().remove(commandName);
                    notifyPlugin.getLogger().atWarning().log("Removing existent alias (" + commandName + ") from " + existingCommand.getOwner().getName() + "!");
                }
            }
        }catch (Exception e){
            EverNifeCore.getLog().warning("Failed to UNREGISTER command [" +  commandName + "]");
            e.printStackTrace();
        }
    }

}
