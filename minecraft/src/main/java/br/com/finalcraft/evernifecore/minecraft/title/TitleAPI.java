package br.com.finalcraft.evernifecore.minecraft.title;

import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TitleAPI {

    private static final Map<UUID, PlayerTitleManager> MANAGER_MAP = new ConcurrentHashMap<>();

    public static void send(Player player, String title, String subTitle){
        TitleMessage.of(title, subTitle).send(player);
    }

    public static void send(Player player, TitleMessage message){
        if (MCVersion.isLowerEquals(MCVersion.v1_7_10)) return;//Title is not present on 1_7_10

        PlayerTitleManager playerTitleManager = MANAGER_MAP.get(player.getUniqueId());

        if (playerTitleManager == null || playerTitleManager.isTerminated()){
            playerTitleManager = new PlayerTitleManager(player);
            MANAGER_MAP.put(player.getUniqueId(), playerTitleManager);
        }

        playerTitleManager.addMessage(message);
    }

    public static void clear(Player player){
        if (MCVersion.isLowerEquals(MCVersion.v1_7_10)) return;//Title is not present on 1_7_10

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

    // Drop a player's manager so a disconnected player does not leak an entry in MANAGER_MAP.
    public static void clearReferences(UUID playerUuid){
        MANAGER_MAP.remove(playerUuid);
    }
}
