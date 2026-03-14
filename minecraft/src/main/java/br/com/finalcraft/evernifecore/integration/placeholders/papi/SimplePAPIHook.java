package br.com.finalcraft.evernifecore.integration.placeholders.papi;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SimplePAPIHook extends PlaceholderHook {

    private final Plugin plugin;
    private final PAPIRegexReplacer PAPI_REGEX_REPLACER;
    private final boolean isPDSection;

    public SimplePAPIHook(Plugin plugin, PAPIRegexReplacer PAPI_REGEX_REPLACER) {
        this.plugin = plugin;
        this.PAPI_REGEX_REPLACER = PAPI_REGEX_REPLACER;
        this.isPDSection = PDSection.class.isAssignableFrom(PAPI_REGEX_REPLACER.getReferClass());
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @Nonnull String placeholder) {
        return this.onRequest(player, placeholder);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @Nonnull String placeholder) {
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
                return null; //The placeholder has not been parsed, this is not an error, it simply does not exist
            }

            return parsedPlaceholder;
        }catch (Exception e){
            this.plugin.getLogger().warning("Failed to parse the Placeholder [" + placeholder + "]");
            e.printStackTrace();
            return "[ErrorOnPlaceholder=='%" + placeholder + "%']";
        }

    }
}
