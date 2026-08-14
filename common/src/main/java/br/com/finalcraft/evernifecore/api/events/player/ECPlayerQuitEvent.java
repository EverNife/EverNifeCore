package br.com.finalcraft.evernifecore.api.events.player;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;

/**
 * Fired when the player leaves, BEFORE the quit detach and flush start: a handler still sees the
 * {@link PlayerData} attached and its sections cached, and whatever it dirties here rides the flush
 * that follows.
 */
public class ECPlayerQuitEvent extends ECEvent implements IECEvent {

    private final PlayerData playerData;

    public ECPlayerQuitEvent(PlayerData playerData) {
        this.playerData = playerData;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public FPlayer getPlayer() {
        return playerData.getPlayer();
    }

    /** The section, waited for - see {@link ECPlayerFullyLoggedInEvent#getPDSection(Class)}. */
    public <T extends PDSection> T getPDSection(Class<T> pdSectionClass) {
        return playerData.getPDSection(pdSectionClass).join();
    }

}
