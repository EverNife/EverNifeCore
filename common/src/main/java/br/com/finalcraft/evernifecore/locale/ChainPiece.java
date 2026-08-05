package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

/**
 * One link of an appended message: something that knows how to produce its own {@link FancyText}
 * for one recipient. A locale-backed piece resolves the recipient's language here; a raw
 * {@link FancyText} piece just hands over a copy of itself.
 *
 * <p>The result is always a fresh instance the caller may reshape, so appending a message never
 * changes the message that was appended.</p>
 */
@FunctionalInterface
public interface ChainPiece {

    FancyText renderFor(@Nullable FCommandSender sender);

    /** The recipient's locale text for {@code localeMessage}, falling back to the plugin's default. */
    static ChainPiece of(LocaleMessage localeMessage) {
        return sender -> (sender == null
                ? localeMessage.getDefaultFancyText().copy()
                : localeMessage.getFancyText(sender)).copy();
    }

    static ChainPiece of(FancyText fancyText) {
        return sender -> fancyText.copy();
    }
}
