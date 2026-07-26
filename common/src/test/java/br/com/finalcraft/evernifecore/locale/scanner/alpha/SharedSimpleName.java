package br.com.finalcraft.evernifecore.locale.scanner.alpha;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;

/**
 * One half of a locale-key collision fixture: this class and its twin in the sibling {@code beta}
 * package share a simple name and a field name, which is all the scanner's key is built from.
 */
public class SharedSimpleName {

    @FCLocale(text = "Greeting from alpha")
    public static LocaleMessage GREETING;
}
