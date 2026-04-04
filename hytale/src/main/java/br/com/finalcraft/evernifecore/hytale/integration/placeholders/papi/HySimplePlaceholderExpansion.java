package br.com.finalcraft.evernifecore.hytale.integration.placeholders.papi;

import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

public class HySimplePlaceholderExpansion extends PlaceholderExpansion {

    private final ECPluginData plugin;
    private final HySimplePAPIHook hySimplePAPIHook;
    private final String pluginBaseID;

    public HySimplePlaceholderExpansion(ECPluginData plugin, String pluginBaseID, HySimplePAPIHook hySimplePAPIHook) {
        this.plugin = plugin;
        this.pluginBaseID = pluginBaseID;
        this.hySimplePAPIHook = hySimplePAPIHook;
    }

    @Override
    public @Nonnull String getName() {
        return plugin.getPluginData().getName();
    }

    @Override
    public @Nonnull String getIdentifier() {
        return pluginBaseID;
    }

    @Override
    public @Nonnull String getAuthor() {
        return plugin.getPluginData().getAuthor();
    }

    @Override
    public @Nonnull String getVersion() {
        return plugin.getPluginData().getVersion();
    }

    @Override
    public String onPlaceholderRequest(PlayerRef player, @NotNull String params) {
        return hySimplePAPIHook.onPlaceholderRequest(player, params);
    }

    @Override
    public boolean persist() {
        return true;
    }

}
