package br.com.finalcraft.evernifecore.hytale.integration.placeholders;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.PlaceholderAPIPlugin;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.integration.placeholders.papi.HySimplePAPIHook;
import br.com.finalcraft.evernifecore.hytale.integration.placeholders.papi.HySimplePlaceholderExpansion;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIRegexReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class HyPAPIIntegration {

    // The Name in PlaceholderAPI-Hytale's manifest.json
    static final String PAPI_PLUGIN_NAME = "PlaceholderAPI";

    private static Boolean isApiOnClasspath = null;

    /**
     * Whether PlaceholderAPI is installed AND enabled. Its classes alone are not enough: a plugin that failed to
     * start, or was shut down, leaves them loadable. Asked anew every time, so an answer given before
     * PlaceholderAPI started does not stick.
     */
    public static boolean isPresent(){
        return isApiOnClasspath() && EverNifeCore.getPlatform().isPluginEnabled(PAPI_PLUGIN_NAME);
    }

    /** Whether the API class {@code parse} calls is on the classpath; initializing it needs no live server. */
    static boolean isApiOnClasspath(){
        if (isApiOnClasspath == null){
            // isClassLoaded initializes the class, and a plugin main class may only initialize against a live server.
            isApiOnClasspath = FCReflectionUtil.getClasses().isClassLoaded("at.helpch.placeholderapi.PlaceholderAPI");
        }
        return isApiOnClasspath;
    }

    public static <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType){
        PAPIRegexReplacer papiRegexReplacer = new PAPIRegexReplacer(playerDataType);

        if (isPresent()){
            //Inner caller prevents 'java.lang.NoClassDefFoundError'
            InnerPAPIRegisterer.register(plugin, pluginBaseID, papiRegexReplacer);
            EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getMetaInfo().getName() + " with prefix '"  + pluginBaseID + "' using Modern PAPI method.");
        }

        return papiRegexReplacer.getRegexReplacer();
    }

    public static String parse(@Nullable FPlayer player, @Nonnull String text){
        PlayerRef playerRef = player == null ? null : ((HytaleFPlayer) player).getPlayerRef();
        if (isPresent() && playerRef != null){
            text = PlaceholderAPI.setPlaceholders(playerRef, text);
        }

        return FCColorUtil.colorfy(text);
    }

    private static class InnerPAPIRegisterer {

        public static void register(ECPluginData plugin, String pluginBaseID, PAPIRegexReplacer papiRegexReplacer) {
            HySimplePAPIHook hySimplePAPIHook = new HySimplePAPIHook(plugin, papiRegexReplacer);
            PlaceholderAPIPlugin.instance().localExpansionManager().register(new HySimplePlaceholderExpansion(
                    plugin,
                    pluginBaseID,
                    hySimplePAPIHook
            ));
        }

    }

}
