package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class ECCorePAPIPlaceholders {

    public static void initialize(JavaPlugin plugin){
        PAPIIntegration.createPlaceholderIntegration(plugin, "evernifecore", PlayerData.class)
                .addParser("player_name", playerData -> playerData.getPlayerName())
                .addParser("player_uuid", playerData -> playerData.getUniqueId())
                .addParser("player_last_seen", playerData -> FCTimeUtil.getFormatted(playerData.getLastSeen()))
                .addParser("player_last_seen_millis", playerData -> playerData.getLastSeen());
    }

}
