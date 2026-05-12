package br.com.finalcraft.evernifecore.api.common.player;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.platoverride.player.FPlayerAdapter;
import jakarta.annotation.Nonnull;

public interface FPlayer extends FCommandSender {

    boolean isOnline();

    default void kick(@Nonnull String reason) {
        //Do kick
    }

    public default FPlayerAdapter adapter(){
        return (FPlayerAdapter) this;
    }

    public FLocation getLocation();

    public boolean teleportTo(FLocation targetLocation);
}
