package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigManager;

public class ECSettings {

    public static boolean useNamesInsteadOfUUIDToStorePlayerData = false;
    public static String ZONE_ID_OF_DAY_OF_TODAY = "America/Sao_Paulo";

    public static void initialize(){

        useNamesInsteadOfUUIDToStorePlayerData = ConfigManager.getMainConfig()
                .getOrSetDefaultValue("Settings.useNamesInsteadOfUUIDToStorePlayerData", false);

        ZONE_ID_OF_DAY_OF_TODAY = ConfigManager.getMainConfig()
                .getOrSetDefaultValue("Settings.ZONE_ID_OF_DAY_OF_TODAY", "America/Sao_Paulo");


        ConfigManager.getMainConfig().saveIfNewDefaults();
    }

}
