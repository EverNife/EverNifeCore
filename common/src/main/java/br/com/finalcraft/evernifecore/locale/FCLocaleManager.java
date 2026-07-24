package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;

public class FCLocaleManager {

    public static String DEFAULT_EVERNIFECORE_LOCALE = LocaleType.EN_US;

    public static String getLangOf(ECPluginData plugin){
        return ECPluginManager.getOrCreateECorePluginData(plugin).getPluginLanguage();
    }

    public static void updateEverNifeCoreLocale(){
        DEFAULT_EVERNIFECORE_LOCALE = EverNifeCore.instance.getEcPluginData().getPluginLanguage();
    }

    public static void loadLocale(ECPluginData plugin, Class<?>... classes){
        loadLocale(plugin, false, classes);
    }

    public static void loadLocale(ECPluginData plugin, boolean silent, Class<?>... classes){

        for (Class<?> clazz : classes) {
            //Load all locales on the class
            FCLocaleScanner.scanForLocale(plugin, silent, clazz);
        }

        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);

        if (ecPluginData.isMarkedForLocaleReload()){
            ecPluginData.reloadAllCustomLocales();
        }

    }

}
