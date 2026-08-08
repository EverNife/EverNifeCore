package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

/**
 * The text of one line, for one entry and one reader. A {@code LocaleMessage} answers in the
 * reader's language; a plain String or FancyText answers the same thing to everybody, which in
 * practice is the default language.
 *
 * <p>Always a fresh instance: the line is baked with this row's values right after.</p>
 */
@FunctionalInterface
interface RowTemplate<OBJ> {

    FancyText textFor(OBJ object, @Nullable FCommandSender reader);
}
