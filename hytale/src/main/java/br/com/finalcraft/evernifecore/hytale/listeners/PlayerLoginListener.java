package br.com.finalcraft.evernifecore.hytale.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
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
            //Setup-connect runs off the main thread - join() on the async login pipeline.
            //Bounded by a timeout: a hung backend must not hang the login thread forever.
            try {
                PlayerController.handleLoginWithTimeout(event.getUuid(), event.getUsername()).join();
            } catch (Throwable loginFailure) {
                EverNifeCore.getLog().severe(
                        "Failed to load PlayerData for " + event.getUsername() + " (storage down?): "
                                + loginFailure.getMessage());
            }
        });

        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerConnectEvent.class, (event) -> {
            var playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());

            FPlayer fPlayer = FCHytaleUtil.adapt(playerRef);
            PlayerData playerData = PlayerController.getLoaded(fPlayer);

            if (playerData != null){
                //[Holding onto a Player.class instance] is bad practice, but on hytale, what isn't :D
                playerData.setPlayer(fPlayer);
            } else {
                //the setup-connect load failed/timed out and Hytale has no deny API, so the player
                //got in anyway - retry now (storage may be back) instead of leaving a data-less session
                EverNifeCore.getLog().warning("Player " + fPlayer.getName() + " connected WITHOUT"
                        + " PlayerData (login-time load failed) - retrying the load now.");
                PlayerController.handleLogin(fPlayer.getUniqueId(), fPlayer.getName())
                        .whenComplete((loaded, retryError) -> {
                            if (loaded != null && fPlayer.isOnline()){
                                loaded.setPlayer(fPlayer);
                            } else if (retryError != null){
                                EverNifeCore.getLog().severe("Retry-load of PlayerData for "
                                        + fPlayer.getName() + " failed too: " + retryError.getMessage());
                            }
                        });
            }
        });

        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, (event) -> {
            var playerRef = event.getPlayerRef();

            PlayerData playerData = PlayerController.getLoaded(FCHytaleUtil.adapt(playerRef));

            if (playerData != null){
                //Detach + durably flush this player off the quit thread (bounded async; retried on
                //a storage outage, never dropped). Working-set sections evict after a short grace.
                PlayerController.handlePlayerQuit(playerData.getUniqueId());
            }
        });
    }

}
