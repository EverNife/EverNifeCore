package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class FCLocaleManager {

    public static String DEFAULT_EVERNIFECORE_LOCALE = LocaleType.EN_US.name();

    public static HashMap<UUID, String> PLAYER_LOCALES = new HashMap<>();

    public static String getLangOf(Plugin plugin){
        return getLocalization(plugin).getPluginLang();
    }

    public static String getLangOf(Player player){
        return PLAYER_LOCALES.get(player.getUniqueId());
    }

    public static void updateEverNifeCoreLocale(){
        //Sets the DEFAULT LOCALE for all plugins on first load!
        DEFAULT_EVERNIFECORE_LOCALE = getLocalization(EverNifeCore.instance).getPluginLang();
    }

    public static void reloadLocale(Plugin plugin){
        PluginLocalization pluginLocalization = getLocalization(plugin);
        PLUGIN_LOCALES.remove(plugin.getName()); //Reload everything!
        if (pluginLocalization.getClassList().size() > 0){
            loadLocale(plugin, pluginLocalization.getClassList().toArray(new Class[0])); //This means a reload of all classes, including commands!
        }
    }

    public static void loadLocale(Plugin plugin, Class... classes){

        PluginLocalization pluginLocalization = getLocalization(plugin);

        for (Class aClass : classes) {
            if (pluginLocalization.getClassList().contains(aClass)){
                pluginLocalization.getClassList().addAll(Arrays.asList(classes));
                PLUGIN_LOCALES.remove(plugin.getName()); //Reload everything!
                loadLocale(plugin, pluginLocalization.getClassList().toArray(new Class[0])); //This means a reload of all classes, including commands!
                return;
            }
        }

        boolean isDefaultLang = Arrays.stream(LocaleType.values()).map(Enum::name).filter(s -> s.equals(pluginLocalization.getPluginLang())).findAny().isPresent();
        Config CUSTOM_LANG = isDefaultLang ? null : new Config(plugin, "localization/lang_" + pluginLocalization.getPluginLang() + ".yml");

        for (Class aClass : classes) {
            pluginLocalization.addLocaleClass(aClass);
            String simpleName = aClass.getSimpleName();

            List<LocaleMessageImp> localeMessageList = FCLocaleScanner.scanForLocale(plugin, aClass);

            for (LocaleMessageImp localeMessage : localeMessageList) {
                if (CUSTOM_LANG != null) { //We are using a custom lang, need to add locale messages
                    if (!CUSTOM_LANG.contains(simpleName + "." + localeMessage.getKey())){
                        CUSTOM_LANG.setDefaultValue(simpleName + "." + localeMessage.getKey(), localeMessage.getFancyText(LocaleType.EN_US.name()));
                    }

                    FancyText fancyText = CUSTOM_LANG.getFancyText(simpleName + "." + localeMessage.getKey());
                    localeMessage.addLocale(fancyText, pluginLocalization.getPluginLang());
                }
                FancyText fancyText = localeMessage.getFancyText(pluginLocalization.getPluginLang());
                localeMessage.setCachedFancyTextTo(fancyText);
            }

        }

        if (CUSTOM_LANG != null){
            CUSTOM_LANG.saveIfNewDefaults();
        }

    }

    private static final HashMap<String, PluginLocalization> PLUGIN_LOCALES = new HashMap<>();

    private static PluginLocalization getLocalization(final Plugin plugin){
        return PLUGIN_LOCALES.computeIfAbsent(plugin.getName(), pluginName -> {

            Config localization_config = new Config(plugin, "localization/localization_config.yml");
            String pluginDefaultLang = localization_config.getOrSetDefaultValue("Localization.fileName", "lang_" + DEFAULT_EVERNIFECORE_LOCALE + ".yml")
                    .replace(".yml","")
                    .replace("lang_","");
            localization_config.saveIfNewDefaults();

            plugin.getLogger().info("[FCLocale] Set default locale to [" + pluginDefaultLang  +"]!");

            return new PluginLocalization(pluginDefaultLang);
        });
    }

}
