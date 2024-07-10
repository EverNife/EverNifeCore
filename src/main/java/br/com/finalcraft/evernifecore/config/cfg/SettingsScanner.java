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
import java.util.Arrays;
import java.util.List;

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

                Object newValue;
                if (defValue instanceof List){
                    newValue = config.getOrSetDefaultValue(settings.key(), (List<? extends Object>) defValue, comment);
                }else {
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
                        newValue = argParser.parserArgument(Bukkit.getConsoleSender(), new Argumento(newValue.toString()));
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

}
