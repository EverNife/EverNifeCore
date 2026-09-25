package br.com.finalcraft.evernifecore.api.common.player;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.platoverride.player.FPlayerAdapter;
import jakarta.annotation.Nonnull;

public interface FPlayer extends FCommandSender {

    boolean isOnline();

    /**
     * Whether the server has a record of this player from an earlier session, as the platform itself keeps
     * it. Bukkit answers from the player file ({@code OfflinePlayer.hasPlayedBefore()}), so during a first
     * session it stays {@code false} until the player leaves. Hytale answers whether its universe player
     * storage holds a file for this player; the server writes that file on its own save schedule, so there it
     * can turn {@code true} while the first session is still running.
     */
    boolean hasPlayedBefore();

    default void kick(@Nonnull String reason) {
        //Do kick
    }

    public default FPlayerAdapter adapter(){
        return (FPlayerAdapter) this;
    }

    public FLocation getLocation();

    public boolean teleportTo(FLocation targetLocation);
}
