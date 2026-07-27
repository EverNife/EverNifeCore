package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

public interface LocaleMessage extends ILocaleMessageBase{

    SendCustom custom();

    FancyText getFancyText(String localeName);

    /**
     * Whether any language at all was registered for this message. Ask before sending when there is
     * something else to do about an undefined message - {@link #getDefaultFancyText()} never fails,
     * it renders the key instead, so nothing else reports it.
     */
    boolean isDefined();

    /**
     * The text of the plugin's own language, falling back to the first registered one. Never
     * {@code null}: a message with no registered language at all renders as
     * {@code [LOCALE_NOT_DEFINED:<key>]} - see {@link #isDefined()}.
     */
    FancyText getDefaultFancyText();

}
