package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;

import java.util.HashMap;
import java.util.UUID;

public class FCLocaleManager {

    public static String DEFAULT_EVERNIFECORE_LOCALE = LocaleType.EN_US;

    public static HashMap<UUID, String> PLAYER_LOCALES = new HashMap<>();

    public static String getLangOf(ECPluginData plugin){
        return ECPluginManager.getOrCreateECorePluginData(plugin).getPluginLanguage();
    }

    public static String getLangOf(FPlayer player){
        return getLangOf(player.getUniqueId());
    }

    public static String getLangOf(UUID playerUuid){
        return PLAYER_LOCALES.get(playerUuid);
    }

    public static void updateEverNifeCoreLocale(){
        DEFAULT_EVERNIFECORE_LOCALE = EverNifeCore.instance.getEcPluginData().getPluginLanguage();
    }

    public static void loadLocale(ECPluginData plugin, Class... classes){
        loadLocale(plugin, false, classes);
    }

    public static void loadLocale(ECPluginData plugin, boolean silent, Class... classes){

        for (Class clazz : classes) {
            //Load all locales on the class
            FCLocaleScanner.scanForLocale(plugin, silent, clazz);
        }

        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);

        if (ecPluginData.isMarkedForLocaleReload()){
            ecPluginData.reloadAllCustomLocales();
        }

    }

}
