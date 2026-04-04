package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarAPI {

    private static final boolean THIS_SERVER_SUPPORTS_ACTIONBAR = false;
    private static Map<UUID, PlayerActionBarManager> PLAYER_ACTION_BAR_MAP = new HashMap<>();

    public static void send(FPlayer player, String message){
        ActionBarMessage.of(message).send(player);
    }

    public static void send(FPlayer player, ActionBarMessage actionBarMessage){
        if (!THIS_SERVER_SUPPORTS_ACTIONBAR){
            return;
        }

        PlayerActionBarManager playerActionBarManager = PLAYER_ACTION_BAR_MAP.get(player.getUniqueId());

        if (playerActionBarManager == null || playerActionBarManager.isTerminated()){
            playerActionBarManager = new PlayerActionBarManager(player);
            PLAYER_ACTION_BAR_MAP.put(player.getUniqueId(), playerActionBarManager);
        }

        playerActionBarManager.addMessage(actionBarMessage);
    }

    public static void clear(FPlayer player){
        if (!THIS_SERVER_SUPPORTS_ACTIONBAR){
            //This server is on 1.7.10, and we don't have NecroTempus
            return;
        }

        PlayerActionBarManager playerActionBarManager = PLAYER_ACTION_BAR_MAP.get(player.getUniqueId());

        if (playerActionBarManager != null && playerActionBarManager.hasStarted() && !playerActionBarManager.isTerminated()){
            playerActionBarManager.terminate();
            PLAYER_ACTION_BAR_MAP.remove(player.getUniqueId());
        }

        PlayerActionBarManager.sendActionBarMessage(player, FancyText.of());
    }

    public static ActionBarMessage.Builder message(String message){
        return ActionBarMessage.of(message);
    }

    public static void clearReferences(UUID playerUuid){
        PLAYER_ACTION_BAR_MAP.remove(playerUuid);
    }
}
