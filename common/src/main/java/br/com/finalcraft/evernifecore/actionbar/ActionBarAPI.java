package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBarAPI {

    private static Map<UUID, PlayerActionBarManager> PLAYER_ACTION_BAR_MAP = new ConcurrentHashMap<>();

    public static void send(FPlayer player, String message){
        ActionBarMessage.of(message).send(player);
    }

    public static void send(FPlayer player, ActionBarMessage actionBarMessage){
        if (!EverNifeCore.getPlatform().serverSupportsActionBar()){
            return;
        }

        //Atomic get-or-create so concurrent senders never spawn two managers for the same player
        PlayerActionBarManager playerActionBarManager = PLAYER_ACTION_BAR_MAP.compute(
                player.getUniqueId(),
                (uuid, mgr) -> (mgr == null || mgr.isTerminated()) ? new PlayerActionBarManager(player) : mgr
        );

        playerActionBarManager.addMessage(actionBarMessage);
    }

    public static void clear(FPlayer player){
        if (!EverNifeCore.getPlatform().serverSupportsActionBar()){
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
