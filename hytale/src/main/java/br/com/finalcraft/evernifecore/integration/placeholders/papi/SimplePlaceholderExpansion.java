package br.com.finalcraft.evernifecore.integration.placeholders.papi;

import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

public class SimplePlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final SimplePAPIHook simplePAPIHook;
    private final String pluginBaseID;

    public SimplePlaceholderExpansion(JavaPlugin plugin, String pluginBaseID, SimplePAPIHook simplePAPIHook) {
        this.plugin = plugin;
        this.pluginBaseID = pluginBaseID;
        this.simplePAPIHook = simplePAPIHook;
    }

    @Override
    public @Nonnull String getName() {
        return plugin.getManifest().getName();
    }

    @Override
    public @Nonnull String getIdentifier() {
        return pluginBaseID;
    }

    @Override
    public @Nonnull String getAuthor() {
        return plugin.getManifest().getAuthors().getFirst().getName();
    }

    @Override
    public @Nonnull String getVersion() {
        return plugin.getManifest().getVersion().toString();
    }

    @Override
    public String onPlaceholderRequest(PlayerRef player, @NotNull String params) {
        return simplePAPIHook.onPlaceholderRequest(player, params);
    }

    @Override
    public boolean persist() {
        return true;
    }

}
