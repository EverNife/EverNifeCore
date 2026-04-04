package br.com.finalcraft.evernifecore.hytale.integration.placeholders.papi;

import at.helpch.placeholderapi.PlaceholderHook;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIRegexReplacer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

public class HySimplePAPIHook implements PlaceholderHook {

    private final ECPluginData plugin;
    private final PAPIRegexReplacer PAPI_REGEX_REPLACER;
    private final boolean isPDSection;

    public HySimplePAPIHook(ECPluginData plugin, PAPIRegexReplacer PAPI_REGEX_REPLACER) {
        this.plugin = plugin;
        this.PAPI_REGEX_REPLACER = PAPI_REGEX_REPLACER;
        this.isPDSection = PDSection.class.isAssignableFrom(PAPI_REGEX_REPLACER.getReferClass());
    }

    @Override
    public String onPlaceholderRequest(PlayerRef player, @NotNull String placeholder) {
        IPlayerData playerData = player == null ? null : PlayerController.getPlayerData(player.getUuid());

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
            this.plugin.getLog().warning("Failed to parse the Placeholder [" + placeholder + "]");
            e.printStackTrace();
            return "[ErrorOnPlaceholder=='%" + placeholder + "%']";
        }
    }
}
