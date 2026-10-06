package br.com.finalcraft.evernifecore.region.flag;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** A yes/no flag value that can also be left open. Whether TRUE allows or denies is the flag's name to tell. */
public enum Tristate {
    TRUE, FALSE, UNDEFINED;

    /**
     * The value named by {@code text}, case-insensitively - what a command or a {@code k=v} pair carries.
     *
     * @throws IllegalArgumentException when the text names none of the three
     */
    @JsonCreator
    public static Tristate fromText(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException notOneOfThem) {
            throw new IllegalArgumentException("'" + text + "' is not a tristate: write true, false or undefined.");
        }
    }
}
