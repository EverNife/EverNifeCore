package br.com.finalcraft.evernifecore.hytale.listeners;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.hytale.loader.EverNifeCoreHytalePlugin;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class PlayerLoginListener implements ECListener {

    @Override
    public void onRegister() {
        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerSetupConnectEvent.class, (event) -> {
            PlayerController.handlePlayerAsyncPreUUIDToNameCalculation(event.getUuid(), event.getUsername());
        });

        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerConnectEvent.class, (event) -> {
            var playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());

            FPlayer fPlayer = FCHytaleUtil.adapt(playerRef);
            PlayerData playerData = PlayerController.getPlayerData(fPlayer);

            if (playerData != null){
                //[Store an instance of a Player.class] it is a bad practice, but in hytale, what is not :D
                playerData.setPlayer(fPlayer);
            }
        });

        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, (event) -> {
            var playerRef = event.getPlayerRef();

            PlayerData playerData = PlayerController.getPlayerData(FCHytaleUtil.adapt(playerRef));

            if (playerData != null){
                //[Store an instance of a Player.class] it is a bad practice, but in hytale, what is not :D
                playerData.setPlayer(null);
            }
        });
    }

}
