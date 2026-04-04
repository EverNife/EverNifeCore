package br.com.finalcraft.evernifecore.hytale.integration.placeholders;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.PlaceholderAPIPlugin;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.hytale.integration.placeholders.papi.HySimplePAPIHook;
import br.com.finalcraft.evernifecore.hytale.integration.placeholders.papi.HySimplePlaceholderExpansion;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIRegexReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class HyPAPIIntegration {

    private static Boolean isPresent = null;

    public static boolean isPresent(){
        if (isPresent == null){
            isPresent = FCReflectionUtil.isClassLoaded("at.helpch.placeholderapi.PlaceholderAPIPlugin");
        }
        return isPresent;
    }

    public static <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType){
        PAPIRegexReplacer papiRegexReplacer = new PAPIRegexReplacer(playerDataType);

        //Inner caller prevents 'java.lang.NoClassDefFoundError'
        InnerPAPIRegisterer.register(plugin, pluginBaseID, papiRegexReplacer);

        EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getPluginData().getName() + " with prefix '"  + pluginBaseID + "' using Modern PAPI method.");

        return papiRegexReplacer.getRegexReplacer();
    }

    public static String parse(@Nullable PlayerRef playerRef, @Nonnull String text){
        if (isPresent()){
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
