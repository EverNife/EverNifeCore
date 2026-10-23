package br.com.finalcraft.evernifecore.minecraft.integration.placeholders;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIRegexReplacer;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.integration.placeholders.papi.McSimplePAPIHook;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class McPAPIIntegration {

    private static Boolean isApiOnClasspath = null;

    // Plugin name -> how to take each of its expansions off PlaceholderAPI again.
    private static final Map<String, List<Runnable>> UNREGISTER_ON_DISABLE = new ConcurrentHashMap<>();

    /**
     * Whether PlaceholderAPI is installed AND enabled. Its classes alone are not enough: a plugin that failed to enable
     * leaves them loadable, and calling into it then dies with a {@code NoClassDefFoundError}. Asked anew every time,
     * so an answer given before PlaceholderAPI enabled does not stick.
     */
    public static boolean isPresent(){
        return isApiOnClasspath() && Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** Whether the API class {@code parse} calls is on the classpath; initializing it needs no live server. */
    static boolean isApiOnClasspath(){
        if (isApiOnClasspath == null){
            isApiOnClasspath = FCReflectionUtil.getClasses().isClassLoaded("me.clip.placeholderapi.PlaceholderAPI");
        }
        return isApiOnClasspath;
    }

    public static <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType){
        PAPIRegexReplacer papiRegexReplacer = new PAPIRegexReplacer(playerDataType);

        if (isPresent()) {
            //Inner caller prevents 'java.lang.NoClassDefFoundError'
            InnerPAPIRegisterer.register(plugin, pluginBaseID, papiRegexReplacer);
        }

        return papiRegexReplacer.getRegexReplacer();
    }

    /**
     * Takes every expansion {@link #createPlaceholderIntegration} registered for {@code pluginName} off
     * PlaceholderAPI, so a plugin disabled and enabled again at runtime does not leave its old expansion
     * answering. Called on that plugin's disable. Best effort: a PlaceholderAPI build that cannot unregister
     * (the old ones a 1.7.10 server runs) leaves that expansion in place until a restart, with a warning.
     */
    public static void unregisterAll(@Nonnull String pluginName){
        List<Runnable> undos = UNREGISTER_ON_DISABLE.remove(pluginName);
        if (undos == null) return;
        for (Runnable undo : undos) {
            try {
                undo.run();
            } catch (Throwable unsupported) {
                EverNifeCore.getLog().warning("Could not unregister a PlaceholderAPI expansion of {} on its disable ({})."
                        + " Its placeholders keep answering from the disabled plugin until the server restarts.",
                        pluginName, unsupported.toString());
            }
        }
    }

    static void unregisterOnDisable(String pluginName, Runnable undo){
        UNREGISTER_ON_DISABLE.computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>()).add(undo);
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

            boolean weAreOnModernPAPI = FCReflectionUtil.getClasses().isClassLoaded("me.clip.placeholderapi.expansion.manager.LocalExpansionManager");
            if (weAreOnModernPAPI == false){
                // Legacy PAPI Support
                EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getMetaInfo().getName() + " with prefix '"  + pluginBaseID + "' using Legacy PAPI method.");
                PlaceholderAPI.registerPlaceholderHook(pluginBaseID, mcSimplePAPIHook);
                unregisterOnDisable(plugin.getMetaInfo().getName(), () -> PlaceholderAPI.unregisterPlaceholderHook(pluginBaseID));
            }else {
                EverNifeCore.getLog().info("Registering PAPI Hook for the plugin " + plugin.getMetaInfo().getName() + " with prefix '"  + pluginBaseID + "' using Modern PAPI method.");
                PlaceholderExpansion expansion = new PlaceholderExpansion() {
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
                };
                PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().register(expansion);
                unregisterOnDisable(plugin.getMetaInfo().getName(), expansion::unregister);
            }
        }
    }

}
