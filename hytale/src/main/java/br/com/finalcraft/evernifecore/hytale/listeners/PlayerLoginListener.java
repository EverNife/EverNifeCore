package br.com.finalcraft.evernifecore.hytale.listeners;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.hytale.loader.HyEverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class PlayerLoginListener implements ECListener {

    @Override
    public void onRegister() {
        HyEverNifeCore.instance.getEventRegistry().registerGlobal(PlayerSetupConnectEvent.class, (event) -> {
            PlayerController.handlePlayerAsyncPreUUIDToNameCalculation(event.getUuid(), event.getUsername());
        });

        HyEverNifeCore.instance.getEventRegistry().registerGlobal(PlayerConnectEvent.class, (event) -> {
            var playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());

            FPlayer fPlayer = FCHytaleUtil.wrap(playerRef);
            PlayerData playerData = PlayerController.getPlayerData(fPlayer);

            if (playerData != null){
                //[Store an instance of a Player.class] it is a bad practice, but in hytale, what is not :D
                playerData.setPlayer(fPlayer);
            }
        });

        HyEverNifeCore.instance.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, (event) -> {
            var playerRef = event.getPlayerRef();

            PlayerData playerData = PlayerController.getPlayerData(FCHytaleUtil.wrap(playerRef));

            if (playerData != null){
                //[Store an instance of a Player.class] it is a bad practice, but in hytale, what is not :D
                playerData.setPlayer(null);
            }
        });
    }

}
