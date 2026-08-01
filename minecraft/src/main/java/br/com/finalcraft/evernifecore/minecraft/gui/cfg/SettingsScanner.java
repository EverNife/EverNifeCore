package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseOutcome;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolvedArguments;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SettingsScanner {

    public static void loadSettings(ECPluginData ecPluginData, Config config, Object instance){

        for (Field declaredField : instance.getClass().getDeclaredFields()) {

            ConfigSetting settings = declaredField.getAnnotation(ConfigSetting.class);

            if (settings != null){

                Object defValue = null;
                try {
                    declaredField.setAccessible(true);
                    defValue = declaredField.get(instance);
                } catch (IllegalAccessException ignored) {
                    ;
                }

                if (defValue == null){
                    ecPluginData.getLog().warning("Failed to load ConfigSetting for [" + instance.getClass().getSimpleName() + " - " + declaredField.toString() + "] As there are no DEFAULT_VALUE set");
                    continue;
                }

                String pluginLanguage = ecPluginData.getPluginLanguage();

                String comment = settings.comment().length > 0
                        ? Arrays.stream(settings.comment())
                            .filter(fcLocale -> fcLocale.lang().equalsIgnoreCase(pluginLanguage))
                            .findFirst()
                            .orElse(settings.comment()[0])
                            .text()
                        : null;

                Object newValue = null;
                if (defValue instanceof List){
                    List<Class<?>> fieldType = getFieldType(declaredField);

                    if (fieldType.size() == 1 && fieldType.get(0) == Integer.class){

                        Object slotObject = config.getValue(settings.key());

                        if (slotObject == null){
                            List<String> slotsAsString = new ArrayList<>();
                            for (int i : (List<Integer>) defValue) {
                                slotsAsString.add(String.valueOf(i));
                            }
                            config.setValueIfAbsent(settings.key(), slotsAsString.stream().collect(Collectors.joining(",","[","]"))); //Store slots like "[1,2,3,4,5]"
                            newValue = defValue;
                        } else if (slotObject instanceof String){
                            String slotString = (String) slotObject;
                            if (!slotString.isEmpty()){
                                newValue = Arrays.stream(slotString.replace("[", "")
                                                .replace("]", "")
                                                .split(","))
                                        .map(value -> Integer.valueOf(value.trim()))
                                        .collect(Collectors.toList());
                                ;
                            }
                        }else {
                            newValue = config.getStringList(settings.key())
                                    .stream()
                                    .mapToInt(value -> Integer.valueOf(value.trim()))
                                    .toArray();
                        }
                    }else {
                        newValue = config.getOrSetValueIfAbsent(settings.key(), (List<? extends Object>) defValue, comment);
                    }
                } else {
                    newValue = config.getOrSetValueIfAbsent(settings.key(), defValue, comment);
                }

                Class<? extends ArgParser> parserClass = null;

                if (ArgParser.class == settings.parser()){
                    //This means the DEFAULT parser, so, we look over the ArgParserManager
                    parserClass = ArgParserManager.getParser(ECPluginManager.getOrCreateECorePluginData(ecPluginData), defValue.getClass());
                }else {
                    parserClass = settings.parser();
                }

                if (parserClass != null){
                    ArgInfo argInfo = ArgInfo.standalone(defValue.getClass(), new ArgData().setName(settings.key()).setContext(settings.context()));

                    try {
                        ArgParser argParser = parserClass.getConstructor(ArgInfo.class).newInstance(argInfo);
                        newValue = parsedOrDefault(ecPluginData, FCBukkitUtil.adapt(Bukkit.getConsoleSender()),
                                argParser, argInfo, newValue, defValue, config, settings.key());
                    } catch (Exception e) {
                        ecPluginData.getLog().warning("Failed to load ConfigSetting for [" + instance.getClass().getSimpleName() + " - " + declaredField.toString() + "] As the parser failed to be created!");
                        e.printStackTrace();
                        continue;
                    }
                }

                try {
                    Class type = declaredField.getType();
                    if (type == Double.class) newValue = Double.valueOf(((Number) newValue).doubleValue());
                    if (type == Integer.class) newValue = Integer.valueOf(((Number) newValue).intValue());
                    if (type == Float.class) newValue = Float.valueOf(((Number) newValue).floatValue());

                    declaredField.set(instance, newValue);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * The stored value as the parser reads it, or {@code defValue} when it cannot be read. Split out
     * of the field loop so the sender that is never messaged is a parameter rather than a lookup on a
     * running server, and the reporting can be exercised without one.
     */
    static Object parsedOrDefault(ECPluginData ecPluginData, FCommandSender sender, ArgParser argParser,
                                  ArgInfo argInfo, Object storedValue, Object defValue, Config config, String key) {
        //A config value, not a command line: there is no dispatch above it, and the ArgInfo is
        //REQUIRED so an unreadable value means "fix it" instead of null
        ParseCall call = new ParseCall(sender, new Argumento(String.valueOf(storedValue)), argInfo,
                null, ResolvedArguments.none(), false);

        ParseOutcome<?> outcome = new ConfigParseEngine(ecPluginData).run(argParser, call);

        if (outcome.isFatal()){
            ecPluginData.getLog().warning("Using default value for " + new ConfigSection(config, key).toString() + " Fix your Config!");
            return defValue;
        }

        return outcome.getValueOrNull();
    }

    private static List<Class<?>> getFieldType(Field field) {
        try {
            Type genericType = field.getGenericType();

            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                Type[] typeArguments = parameterizedType.getActualTypeArguments();

                List<Class<?>> result = new ArrayList<>();
                for (Type type : typeArguments) {
                    if (type instanceof Class<?>) {
                        result.add((Class<?>) type);
                    }
                }
                return result;
            }
        } catch (Exception ignored) {

        }
        return Collections.emptyList();
    }

}
