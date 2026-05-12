package br.com.finalcraft.evernifecore.minecraft.integration.placeholders;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIRegexReplacer;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.integration.placeholders.papi.McSimplePAPIHook;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class McPAPIIntegration {

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

        return papiRegexReplacer.getRegexReplacer();
    }

    public static String parse(@Nullable FPlayer fPlayer, @Nonnull String text){

        Player player = fPlayer == null
                ? null
                : ((MinecraftFPlayer) fPlayer).getPlayer();

        if (isPresent()){
            text = PlaceholderAPI.setPlaceholders(player, text);
        }

        return FCColorUtil.colorfy(text);
    }

    private static class InnerPAPIRegisterer {

        public static void register(ECPluginData plugin, String pluginBaseID, PAPIRegexReplacer papiRegexReplacer) {
            McSimplePAPIHook mcSimplePAPIHook = new McSimplePAPIHook(plugin, papiRegexReplacer);

            boolean weAreOnModernPAPI = FCReflectionUtil.isClassLoaded("me.clip.placeholderapi.expansion.manager.LocalExpansionManager");
            if (weAreOnModernPAPI == false){
                // Legacy PAPI Support
                EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getMetaInfo().getName() + " with prefix '"  + pluginBaseID + "' using Legacy PAPI method.");
                PlaceholderAPI.registerPlaceholderHook(pluginBaseID, mcSimplePAPIHook);
            }else {
                EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getMetaInfo().getName() + " with prefix '"  + pluginBaseID + "' using Modern PAPI method.");
                PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().register(new PlaceholderExpansion() {
                    @Override
                    public @Nonnull String getName() {
                        return plugin.getMetaInfo().getName();
                    }

                    @Override
                    public @Nonnull String getIdentifier() {
                        return pluginBaseID;
                    }

                    @Override
                    public @Nonnull String getAuthor() {
                        return plugin.getMetaInfo().getAuthor();
                    }

                    @Override
                    public @Nonnull String getVersion() {
                        return plugin.getMetaInfo().getVersion();
                    }

                    @Override
                    public @Nullable String onRequest(OfflinePlayer player, @Nonnull String params) {
                        return mcSimplePAPIHook.onRequest(player, params);
                    }

                    @Override
                    public boolean persist() {
                        return true;
                    }
                });
            }
        }
    }

}
