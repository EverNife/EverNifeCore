package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.version.MCVersion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarAPI {

    private static Map<UUID, PlayerActionBarManager> PLAYER_ACTION_BAR_MAP = new HashMap<>();

    public static void send(Player player, String message){
        ActionBarMessage.of(message).send(player);
    }

    public static void send(Player player, ActionBarMessage actionBarMessage){
        if (MCVersion.isLowerEquals1_7_10()) return;//ActionBar is not present on 1_7_10

        PlayerActionBarManager playerActionBarManager = PLAYER_ACTION_BAR_MAP.get(player.getUniqueId());

        if (playerActionBarManager == null || playerActionBarManager.isTerminated()){
            playerActionBarManager = new PlayerActionBarManager(player);
            PLAYER_ACTION_BAR_MAP.put(player.getUniqueId(), playerActionBarManager);
        }

        playerActionBarManager.addMessage(actionBarMessage);
    }

    public static void clear(Player player){
        if (MCVersion.isLowerEquals1_7_10()) return;//ActionBar is not present on 1_7_10

        PlayerActionBarManager playerActionBarManager = PLAYER_ACTION_BAR_MAP.get(player.getUniqueId());

        if (playerActionBarManager != null && playerActionBarManager.isRunning() && !playerActionBarManager.isTerminated()){
            playerActionBarManager.terminate();
            PLAYER_ACTION_BAR_MAP.remove(player.getUniqueId());
        }

        PlayerActionBarManager.spigot_sendMessage(player, ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    public static ActionBarMessage.Builder message(String message){
        return ActionBarMessage.of(message);
    }
}
