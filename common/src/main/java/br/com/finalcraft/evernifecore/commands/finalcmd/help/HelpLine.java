package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import jakarta.annotation.Nonnull;

/**
 * One {@link HelpLineTemplate} already bound to the path it speaks about, and therefore sendable. It
 * belongs to a single render: what a command hands to a method (or sends on a missing argument) is
 * always one of these, never the shared template it came from.
 */
public final class HelpLine {

    private final ILocaleMessageBase message;

    HelpLine(@Nonnull ILocaleMessageBase message) {
        this.message = message;
    }

    public void sendTo(FCommandSender sender) {
        message.send(sender);
    }

    /** Exactly what {@link #sendTo} would deliver, for whoever wants to compose instead of send. */
    public ILocaleMessageBase getMessage() {
        return message;
    }
}
