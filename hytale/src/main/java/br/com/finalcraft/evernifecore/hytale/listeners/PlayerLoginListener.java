package br.com.finalcraft.evernifecore.hytale.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.hytale.loader.EverNifeCoreHytalePlugin;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class PlayerLoginListener implements ECListener {

    @Override
    public void onRegister() {
        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerSetupConnectEvent.class, (event) -> {
            //Setup-connect runs synchronously on the connect thread - join() on the async login
            //pipeline, bounded by a timeout so a hung backend cannot hang the thread forever.
            try {
                PlayerController.handleLoginWithTimeout(event.getUuid(), event.getUsername()).join();
            } catch (Throwable loginFailure) {
                //Fail closed: deny the connect instead of letting a data-less session in, where a
                //fresh profile could overwrite the player's real data on the next save.
                //PlayerSetupConnectEvent is ICancellable, so this stops the login (the Hytale
                //counterpart of the Bukkit KICK_OTHER deny).
                EverNifeCore.getLog().severe(
                        "Failed to load PlayerData for " + event.getUsername() + " (storage down?): "
                                + loginFailure.getMessage());
                event.setReason(Message.raw("Could not load your player data (storage unavailable)."
                        + " Please try again shortly."));
                event.setCancelled(true);
            }
        });

        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerConnectEvent.class, (event) -> {
            var playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());

            FPlayer fPlayer = FCHytaleUtil.adapt(playerRef);
            PlayerData playerData = PlayerController.getLoaded(fPlayer);

            if (playerData != null){
                //[Holding onto a Player.class instance] is bad practice, but on hytale, what isn't :D
                playerData.setPlayer(fPlayer);
                //connected AND the data is attached - that is what "fully logged in" means here
                ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(playerData, false));
            } else {
                //Setup-connect fails closed now, so a connected player normally has data. Reaching
                //here without it is the odd case - a session that skipped setup-connect (an injected/
                //fake player) or a race - so retry the load defensively rather than leave it data-less.
                EverNifeCore.getLog().warning("Player " + fPlayer.getName() + " connected WITHOUT"
                        + " PlayerData - retrying the load now.");
                PlayerController.handleLogin(fPlayer.getUniqueId(), fPlayer.getName())
                        .whenComplete((loaded, retryError) -> {
                            if (loaded != null && fPlayer.isOnline()){
                                loaded.setPlayer(fPlayer);
                                ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(loaded, false));
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
                //announced while the data is still attached, so a handler can read it and dirty it
                ECEventBus.global().post(new ECPlayerQuitEvent(playerData));

                //Detach + durably flush this player off the quit thread (bounded async; retried on
                //a storage outage, never dropped). Working-set sections evict after a short grace.
                PlayerController.handlePlayerQuit(playerData.getUniqueId());
            }
        });
    }

}
