package br.com.finalcraft.evernifecore.api.common.player;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;

import javax.annotation.Nonnull;

public interface FPlayer extends FCommandSender {

    boolean isOnline();

    default void kick(@Nonnull String reason) {
        //Do kick
    }

}
