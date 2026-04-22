package br.com.finalcraft.evernifecore.api.common.commandsender;

import br.com.finalcraft.evernifecore.api.common.IHasDelegate;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nonnull;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public interface FCommandSender extends IHasDelegate {

    String getName();

    UUID getUniqueId();

    default boolean isPlayer() {
        return getUniqueId() != null;
    }

    default boolean isConsole() {
        return !isPlayer();
    }

    default FPlayer asPlayer() {
        if (this instanceof FPlayer) {
            return (FPlayer) this;
        }
        throw new IllegalArgumentException("Invalid FPlayer Conversion: #" + this.getClass().getSimpleName() + ".asPlayer() was executed but this FCommandSender was not a FPlayer. | The class with delegate [" + this.getDelegate().getClass().getName() + "] is not assignable for a FPlayer.");
    }

    boolean hasPermission(@Nonnull String permission);

    public default void sendMessage(String message) {
        sendMessage(FancyText.of(message.replace("§","&")));
    }

    public default void sendMessage(@Nonnull FancyText message) {
        message.send(this);
    }

    void sendMessage(@Nonnull Component component);


}
