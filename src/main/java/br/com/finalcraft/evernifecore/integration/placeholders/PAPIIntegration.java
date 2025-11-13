package br.com.finalcraft.evernifecore.integration.placeholders;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.integration.placeholders.papi.PAPIRegexReplacer;
import br.com.finalcraft.evernifecore.integration.placeholders.papi.SimplePAPIHook;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PAPIIntegration {

    private static Boolean enabled = null;

    public static <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@NotNull Plugin plugin, @NotNull String pluginBaseID, @NotNull Class<P> playerDataType){
        PAPIRegexReplacer papiRegexReplacer = new PAPIRegexReplacer(playerDataType);
        SimplePAPIHook simplePAPIHook = new SimplePAPIHook(plugin, papiRegexReplacer);

        boolean weAreOnModernPAPI = FCReflectionUtil.isClassLoaded("me.clip.placeholderapi.expansion.manager.LocalExpansionManager");

        if (weAreOnModernPAPI == false){
            // Legacy PAPI Support
            EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getDescription().getName() + " with prefix '"  + pluginBaseID + "' using Legacy PAPI method.");
            PlaceholderAPI.registerPlaceholderHook(pluginBaseID, simplePAPIHook);
        }else {
            EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getDescription().getName() + " with prefix '"  + pluginBaseID + "' using Modern PAPI method.");
            PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().register(new PlaceholderExpansion() {

                @Override
                public @NotNull String getName() {
                    return plugin.getDescription().getName();
                }

                @Override
                public @NotNull String getIdentifier() {
                    return pluginBaseID;
                }

                @Override
                public @NotNull String getAuthor() {
                    return plugin.getDescription().getAuthors().get(0);
                }

                @Override
                public @NotNull String getVersion() {
                    return plugin.getDescription().getVersion();
                }

                @Override
                public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
                    return simplePAPIHook.onRequest(player, params);
                }

                @Override
                public boolean persist() {
                    return true;
                }
            });
        }

        return papiRegexReplacer.getRegexReplacer();
    }

    public static String parse(@Nullable Player player, @NotNull String text){
        if (enabled == null){
            enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        }

        if (enabled){
            text = PlaceholderAPI.setPlaceholders(player, text);
        }

        return FCColorUtil.colorfy(text);
    }

}
