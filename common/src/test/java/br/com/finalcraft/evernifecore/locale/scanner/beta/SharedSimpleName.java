package br.com.finalcraft.evernifecore.locale.scanner.beta;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;

/**
 * The other half of the locale-key collision fixture - see the twin in the sibling {@code alpha}
 * package. Different package, same simple name, same field name, same resulting locale key.
 */
public class SharedSimpleName {

    @FCLocale(text = "Greeting from beta")
    public static LocaleMessage GREETING;
}
