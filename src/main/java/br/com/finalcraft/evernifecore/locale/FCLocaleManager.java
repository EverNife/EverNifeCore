package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.UUID;

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

    public static void loadLocale(Plugin plugin, Class... classes){
        loadLocale(plugin, false, classes);
    }

    public static void loadLocale(Plugin plugin, boolean silent, Class... classes){

        for (Class clazz : classes) {
            //Load all locales on the class
            FCLocaleScanner.scanForLocale(plugin, silent, clazz);
        }

        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePlugin(plugin);

        if (ecPluginData.isMarkedForLocaleReload()){
            ecPluginData.reloadAllCustomLocales();
        }

    }

}
