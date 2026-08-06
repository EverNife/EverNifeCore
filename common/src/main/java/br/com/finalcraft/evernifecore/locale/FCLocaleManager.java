package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

public class FCLocaleManager {

    public static String DEFAULT_EVERNIFECORE_LOCALE = LocaleType.EN_US;

    public static String getLangOf(ECPluginData plugin){
        return ECPluginManager.getOrCreateECorePluginData(plugin).getPluginLanguage();
    }

    /**
     * The language {@code sender} reads content of {@code plugin} in: their own choice when per-player
     * locale is enabled and they made one, the plugin's language otherwise.
     *
     * <p>The player's choice is read cache-only, straight from the {@link LocalePDSection} that login
     * hot-loads, so this never blocks nor touches storage. A sender who is not a player, one whose data
     * is not loaded, and one who never chose all answer the plugin's language.</p>
     */
    public static String getLangOf(@Nullable FCommandSender sender, ECPluginData plugin){
        if (ECSettings.PER_PLAYER_LOCALE && sender instanceof FPlayer){
            PlayerData playerData = PlayerController.getLoaded(sender.getUniqueId());
            if (playerData != null){
                LocalePDSection localeSection = playerData.getPDSectionIfLoaded(LocalePDSection.class);
                if (localeSection != null && localeSection.getLang() != null){
                    return localeSection.getLang();
                }
            }
        }
        return getLangOf(plugin);
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
