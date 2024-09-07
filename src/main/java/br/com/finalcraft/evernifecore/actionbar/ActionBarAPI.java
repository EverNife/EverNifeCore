package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarAPI {

    private static final boolean THIS_SERVER_SUPPORTS_ACTIONBAR; static {
        THIS_SERVER_SUPPORTS_ACTIONBAR = MCVersion.isHigher(MCVersion.v1_7_10) || FCBukkitUtil.isModLoaded("necrotempus");
    }
    private static Map<UUID, PlayerActionBarManager> PLAYER_ACTION_BAR_MAP = new HashMap<>();

    public static void send(Player player, String message){
        ActionBarMessage.of(message).send(player);
    }

    public static void send(Player player, ActionBarMessage actionBarMessage){
        if (!THIS_SERVER_SUPPORTS_ACTIONBAR){
            //This server is on 1.7.10, and we don't have NecroTempus
            return;
        }

        PlayerActionBarManager playerActionBarManager = PLAYER_ACTION_BAR_MAP.get(player.getUniqueId());

        if (playerActionBarManager == null || playerActionBarManager.isTerminated()){
            playerActionBarManager = new PlayerActionBarManager(player);
            PLAYER_ACTION_BAR_MAP.put(player.getUniqueId(), playerActionBarManager);
        }

        playerActionBarManager.addMessage(actionBarMessage);
    }

    public static void clear(Player player){
        if (!THIS_SERVER_SUPPORTS_ACTIONBAR){
            //This server is on 1.7.10, and we don't have NecroTempus
            return;
        }

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
