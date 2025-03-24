package br.com.finalcraft.evernifecore.config.cfg;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SettingsScanner {

    public static void loadSettings(Plugin plugin, Config config, Object instance){

        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);

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
                    plugin.getLogger().warning("Failed to load ConfigSetting for [" + instance.getClass().getSimpleName() + " - " + declaredField.toString() + "] As there are no DEFAULT_VALUE set");
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
                            config.setDefaultValue(settings.key(), slotsAsString.stream().collect(Collectors.joining(",","[","]"))); //Store slots like "[1,2,3,4,5]"
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
                        newValue = config.getOrSetDefaultValue(settings.key(), (List<? extends Object>) defValue, comment);
                    }
                } else {
                    newValue = config.getOrSetDefaultValue(settings.key(), defValue, comment);
                }

                Class<? extends ArgParser> parserClass = null;

                if (ArgParser.class == settings.parser()){
                    //This means the DEFAULT parser, so, we look over the ArgParserManager
                    parserClass = ArgParserManager.getParser(plugin, defValue.getClass());
                }else {
                    parserClass = settings.parser();
                }

                if (parserClass != null){
                    ArgInfo argInfo = new ArgInfo(defValue.getClass(), new ArgData().setContext(settings.context()), -1, ArgRequirementType.REQUIRED);

                    try {
                        ArgParser argParser = parserClass.getConstructor(ArgInfo.class).newInstance(argInfo);
                        newValue = argParser.parserArgument(Bukkit.getConsoleSender(), new Argumento(String.valueOf(newValue)));
                    } catch (ArgParseException ignored) {
                        plugin.getLogger().warning("Using default value for " + new ConfigSection(config, settings.key()).toString() + " Fix your Config!");
                        newValue = defValue;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load ConfigSetting for [" + instance.getClass().getSimpleName() + " - " + declaredField.toString() + "] As the parser failed to be created!");
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
