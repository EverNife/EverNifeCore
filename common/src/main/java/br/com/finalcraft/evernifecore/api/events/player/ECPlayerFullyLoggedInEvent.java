package br.com.finalcraft.evernifecore.api.events.player;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;

/**
 * Fired once the player is fully in-game AND their {@link PlayerData} is loaded and attached to them.
 * A handler can read and change the player's data right away: this is the point where
 * {@link PlayerData#getPlayer()} answers.
 *
 * <p>Each platform decides when that point is reached - the join plus a tick on Bukkit, the connect
 * plus the loaded data on Hytale - and a player who left in between is never announced.</p>
 */
public class ECPlayerFullyLoggedInEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ECPlayerFullyLoggedInEvent.class);
    }

    private final PlayerData playerData;
    private final boolean externalAuthLogin;

    public ECPlayerFullyLoggedInEvent(PlayerData playerData, boolean externalAuthLogin) {
        this.playerData = playerData;
        this.externalAuthLogin = externalAuthLogin;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public FPlayer getPlayer() {
        return playerData.getPlayer();
    }

    /**
     * The section, waited for. The data is loaded by the time this event fires, so a resident section
     * is already there; one that is not yet cached still costs the read on the calling thread.
     */
    public <T extends PDSection> T getPDSection(Class<T> pdSectionClass) {
        return playerData.getPDSection(pdSectionClass).join();
    }

    /**
     * Whether the login only completed once an external authentication flow did - a plugin that logs
     * the player in after the join, such as AuthMe on Bukkit. A platform with no such flow answers
     * {@code false}.
     */
    public boolean isExternalAuthLogin() {
        return externalAuthLogin;
    }

}
