package br.com.finalcraft.evernifecore.api.common.player;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import jakarta.annotation.Nonnull;

public interface FPlayer extends FCommandSender {

    boolean isOnline();

    default void kick(@Nonnull String reason) {
        //Do kick
    }

    public default HytaleFPlayer asHytaleFPlayer(){
        return (HytaleFPlayer) (Object) this;
    }

}
