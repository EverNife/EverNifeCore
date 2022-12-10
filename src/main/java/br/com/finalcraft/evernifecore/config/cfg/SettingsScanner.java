package br.com.finalcraft.evernifecore.config.cfg;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserNumber;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.List;

public class SettingsScanner {

    public static void loadSettings(Plugin plugin, Config config, Object instance){
        for (Field declaredField : instance.getClass().getDeclaredFields()) {

            ConfigSetting settings = declaredField.getAnnotation(ConfigSetting.class);

            if (settings != null){

                Object defValue = null;
                try {
                    defValue = declaredField.get(instance);
                } catch (IllegalAccessException ignored) {
                    ;
                }

                if (defValue == null){
                    plugin.getLogger().warning("Failed to load ConfigSetting for [" + instance.getClass().getSimpleName() + " - " + declaredField.toString() + "] As there is not DEFAULT_VALUE");
                    continue;
                }

                Object newValue;

                if (defValue instanceof List){
                    newValue = config.getOrSetDefaultValue(settings.key(), (List<? extends Object>) defValue, settings.comment().isEmpty() ? null : settings.comment());
                }else {
                    newValue = config.getOrSetDefaultValue(settings.key(), defValue, settings.comment().isEmpty() ? null : settings.comment());
                }

                if (!settings.context().isEmpty()){

                    if (newValue instanceof Number){
                        ArgParserNumber argParserNumber = new ArgParserNumber(new ArgInfo(defValue.getClass(), new ArgData().context(settings.context()), -1, true));
                        try {
                            newValue = argParserNumber.parserArgument(Bukkit.getConsoleSender(), new Argumento(newValue.toString()));
                        }catch (ArgParseException ignored){
                            plugin.getLogger().warning("Using default value for " + new ConfigSection(config, settings.key()).toString() + " Fix your Config!");
                            newValue = defValue;
                        }
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
