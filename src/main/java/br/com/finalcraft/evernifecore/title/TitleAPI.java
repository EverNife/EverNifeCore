package br.com.finalcraft.evernifecore.title;

import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TitleAPI {

    private static Map<UUID, PlayerTitleManager> MANAGER_MAP = new HashMap<>();

    public static void send(Player player, String title, String subTitle){
        TitleMessage.of(title, subTitle).send(player);
    }

    public static void send(Player player, TitleMessage message){
        if (MCVersion.isBellow1_7_10()) return;//Title is not present on 1_7_10

        PlayerTitleManager playerTitleManager = MANAGER_MAP.get(player.getUniqueId());

        if (playerTitleManager == null || playerTitleManager.isTerminated()){
            playerTitleManager = new PlayerTitleManager(player);
            MANAGER_MAP.put(player.getUniqueId(), playerTitleManager);
        }

        playerTitleManager.addMessage(message);
    }

    public static void clear(Player player){
        if (MCVersion.isBellow1_7_10()) return;//Title is not present on 1_7_10

        PlayerTitleManager playerTitleManager = MANAGER_MAP.get(player.getUniqueId());

        if (playerTitleManager != null && playerTitleManager.isRunning() && !playerTitleManager.isTerminated()){
            playerTitleManager.terminate();
            MANAGER_MAP.remove(player.getUniqueId());
        }

        player.resetTitle();
    }

    public static TitleMessage.Builder message(String title, String subTitle){
        return TitleMessage.of(title, subTitle);
    }
}
