package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPlugin;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class FCLocaleManager {

    public static String DEFAULT_EVERNIFECORE_LOCALE = LocaleType.EN_US.name();

    public static HashMap<UUID, String> PLAYER_LOCALES = new HashMap<>();

    public static String getLangOf(Plugin plugin){
        return ECPluginManager.getOrCreateECorePlugin(plugin).getPluginLanguage();
    }

    public static String getLangOf(Player player){
        return PLAYER_LOCALES.get(player.getUniqueId());
    }

    public static void updateEverNifeCoreLocale(){
        DEFAULT_EVERNIFECORE_LOCALE = ECPluginManager.getOrCreateECorePlugin(EverNifeCore.instance).getPluginLanguage();
    }

    public static void reloadLocale(Plugin plugin){
        ECPlugin ecPlugin = ECPluginManager.getOrCreateECorePlugin(plugin);
        HashSet<Class> previousLocales = ecPlugin.clearLocalesAndLoadConfig();
        if (previousLocales.size() > 0){
            loadLocale(plugin, new ArrayList<>(previousLocales).toArray(new Class[0])); //This means reload of all classes, including commands!
        }
    }

    public static void loadLocale(Plugin plugin, Class... classes){

        ECPlugin ecPlugin = ECPluginManager.getOrCreateECorePlugin(plugin);

        for (Class aClass : classes) {
            if (ecPlugin.getLocalizedClasses().contains(aClass)){  //If we add a class that was already been localized, we need to reload everything!
                ecPlugin.getLocalizedClasses().addAll(Arrays.asList(classes));
                reloadLocale(plugin);
                return;
            }
        }

        for (Class aClass : classes) {
            ecPlugin.addLocaleClass(aClass);
            String simpleName = aClass.getSimpleName();

            //Load all hardcoded locales
            List<LocaleMessageImp> localeMessageList = FCLocaleScanner.scanForLocale(plugin, aClass);

            if (ecPlugin.getCustomLangConfig() != null){ //We are using a custom lang, need to add the new fancyTexts to the locale messages
                for (LocaleMessageImp localeMessage : localeMessageList) {

                    //Set a DefaultValue for this Custom LocaleMessage based on the ENGLISH hardcoded LocaleMessage
                    ecPlugin.getCustomLangConfig().setDefaultValue(simpleName + "." + localeMessage.getKey(), localeMessage.getFancyText(LocaleType.EN_US.name()));

                    //Get the Custom FancyText of this LocaleMessage on the custom ConfigFile
                    FancyText fancyText = ecPlugin.getCustomLangConfig().getFancyText(simpleName + "." + localeMessage.getKey());

                    localeMessage.addLocale(ecPlugin.getPluginLanguage(), fancyText);
                }
            }
        }

        if (ecPlugin.getCustomLangConfig() != null){
            ecPlugin.getCustomLangConfig().saveIfNewDefaults();
        }

    }

}
