package br.com.finalcraft.evernifecore.integration.placeholders.papi;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimplePAPIHook extends PlaceholderHook {

    private final Plugin plugin;
    private final PAPIRegexReplacer PAPI_REGEX_REPLACER;
    private final boolean isPDSection;

    public SimplePAPIHook(Plugin plugin, PAPIRegexReplacer PAPI_REGEX_REPLACER) {
        this.plugin = plugin;
        this.PAPI_REGEX_REPLACER = PAPI_REGEX_REPLACER;
        this.isPDSection = PDSection.class.isAssignableFrom(PAPI_REGEX_REPLACER.getReferClass());
    }

    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String placeholder) {
        IPlayerData playerData = player == null ? null : PlayerController.getPlayerData(player);

        if (playerData != null && isPDSection){
            playerData = playerData.getPDSection(PAPI_REGEX_REPLACER.getReferClass());
        }

        try {
            String startingPlaceholder = '%' + placeholder + '%';
            String parsedPlaceholder = PAPI_REGEX_REPLACER
                    .getRegexReplacer()
                    .apply(startingPlaceholder, playerData);

            if (startingPlaceholder.equals(parsedPlaceholder)){
                return null; //The placeholder has not been parsed, this is not an error, it simply did not exist
            }

            return parsedPlaceholder;
        }catch (Exception e){
            this.plugin.getLogger().warning("Failed to parse the Placeholder [" + placeholder + "]");
            e.printStackTrace();
            return "[ErrorOnPlaceholder]";
        }
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return player != null && player.isOnline() ? this.onPlaceholderRequest((Player)player, params) : this.onPlaceholderRequest((Player)null, params);
    }
}
