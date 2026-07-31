package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The words that mean "show me the help" instead of naming something. One source for the whole
 * framework: the traversal consults it to decide whether a token is asking for help, and the
 * executor consults it for the very first word of a command - two places that would otherwise drift
 * apart the first time the list changed.
 * <p>
 * It is an interceptor, never a reservation: a declaration always wins. A child labelled
 * {@code help}, or a capture standing where the word was typed, answers for it, and the word only
 * means help where nothing else claims it - which is why nothing here is refused at registration,
 * and why a server may safely add its own language to the list.
 */
public final class HelpWords {

    /** What the framework ships with: two English, one pt-BR. */
    public static final List<String> DEFAULTS = ImmutableList.of("help", "?", "ajuda");

    private static List<String> words = DEFAULTS;
    private static Set<String> lookup = ImmutableSet.copyOf(DEFAULTS);

    private HelpWords() {
    }

    /**
     * Replaces the words in effect - what the EverNifeCore config feeds in at boot. Idempotent and
     * safe to call again on a reload; an empty (or entirely blank) list is not a way to turn the help
     * off, so it falls back to the shipped words rather than leaving a tree nobody can browse.
     */
    public static void configure(@Nonnull Collection<String> configured) {
        List<String> cleaned = new ArrayList<String>();
        Set<String> normalized = new LinkedHashSet<String>();
        for (String word : configured) {
            if (word == null || word.trim().isEmpty()){
                continue;
            }
            if (normalized.add(word.trim().toLowerCase(Locale.ROOT))){
                cleaned.add(word.trim());
            }
        }

        words = cleaned.isEmpty() ? DEFAULTS : ImmutableList.copyOf(cleaned);
        lookup = cleaned.isEmpty() ? ImmutableSet.copyOf(DEFAULTS) : ImmutableSet.copyOf(normalized);
    }

    /** Whether {@code token} is one of the words, compared case-insensitively. */
    public static boolean isHelpWord(@Nonnull String token) {
        return lookup.contains(token.toLowerCase(Locale.ROOT));
    }

    /** Every word in effect, in declaration order - the first one is what a message spells out. */
    public static List<String> all() {
        return words;
    }
}
