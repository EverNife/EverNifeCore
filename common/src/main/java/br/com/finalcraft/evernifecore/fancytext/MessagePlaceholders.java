package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import br.com.finalcraft.evernifecore.placeholder.parser.SimpleParser;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Everything a single message declares about substitution: the provider answering for its
 * {@code ${key}} placeholders, the replacer that reads those keys out of the text, and any
 * {@link CompoundReplacer} attached to it (PlaceholderAPI pass-through and friends).
 *
 * <p>{@code ${key}} is the only closure this reads. A key written with delimiters is registered
 * exactly as written - so it simply never matches - and warned about once, because a call that
 * stops working has to say so.</p>
 */
public final class MessagePlaceholders {

    // One warning per key, not per call site: the same wrong key declared from a loop or from every
    // message of a plugin must not flood the console.
    private static final Set<String> WARNED_DELIMITED_KEYS = ConcurrentHashMap.newKeySet();

    private final PlaceholderProvider<RenderContext> provider;
    private final RegexReplacer<RenderContext> replacer;
    private CompoundReplacer compoundReplacer = null;

    public MessagePlaceholders() {
        this(new PlaceholderProvider<>());
    }

    private MessagePlaceholders(PlaceholderProvider<RenderContext> provider) {
        this.provider = provider;
        this.replacer = new RegexReplacer<>(Closures.DOLLAR_CURLY, provider);
    }

    public PlaceholderProvider<RenderContext> getProvider() {
        return provider;
    }

    /** Whether this would rewrite anything at all - no declaration and no attached replacer. */
    public boolean isEmpty() {
        return provider.getParserMap().isEmpty() && (compoundReplacer == null || compoundReplacer.isEmpty());
    }

    /** Declares {@code key} exactly as written - see the class javadoc for why it is not normalised. */
    public void declare(String key, String description, Function<RenderContext, Object> parser) {
        warnOnceIfDelimited(key);
        // The memo token is this declaration's own identity: the same key declared by two messages
        // keeps two answers within one render, while a copy of this message shares the answer with
        // its original, because copying shares the SimpleParser this lambda ends up living in.
        Object declaration = new Object();
        provider.addParser(key, description, context -> context.resolveOnce(declaration, () -> parser.apply(context)));
    }

    /** Attaches a replacer that runs after the {@code ${key}} pass, on the same payloads. */
    public void addReplacer(CompoundReplacer replacer) {
        if (this.compoundReplacer == null) {
            this.compoundReplacer = new CompoundReplacer();
        }
        this.compoundReplacer.appendReplacer(replacer);
    }

    /** Whether a replacer is attached - what it would rewrite is opaque to the {@code ${key}} pass. */
    boolean hasReplacer() {
        return compoundReplacer != null && !compoundReplacer.isEmpty();
    }

    /**
     * Adds every declaration of {@code outer} this does not already answer for, plus its replacers.
     * Splicing a chain into another chain has to push what the chain declared down onto the pieces
     * that were relying on it - without overriding a piece that declares the same key for itself.
     */
    void inheritMissing(MessagePlaceholders outer) {
        Map<String, SimpleParser<RenderContext>> own = provider.getParserMap();
        for (Map.Entry<String, SimpleParser<RenderContext>> entry : outer.provider.getParserMap().entrySet()) {
            own.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (outer.compoundReplacer != null) {
            addReplacer(outer.compoundReplacer);
        }
    }

    public @Nullable String apply(@Nullable String text, RenderContext context) {
        if (text == null) {
            return null;
        }
        String resolved = replacer.apply(text, context);
        return compoundReplacer == null ? resolved : compoundReplacer.apply(resolved);
    }

    public MessagePlaceholders copy() {
        MessagePlaceholders copy = new MessagePlaceholders(provider.copy());
        if (this.compoundReplacer != null) {
            copy.compoundReplacer = this.compoundReplacer.copy();
        }
        return copy;
    }

    /**
     * Warns, once per key, that {@code key} was declared with its delimiters and therefore can never
     * match. Public because it is the single point for this: every declaration surface that takes a
     * bare key - a message, a page line - has to say the same thing, and share the same dedup set, so
     * the console is not flooded by the same mistake reported from two engines.
     */
    public static void warnOnceIfDelimited(String key) {
        for (Closures closures : Closures.values()) {
            String head = closures.getHead();
            String tail = closures.getTail();
            if (key.length() <= head.length() + tail.length()) {
                continue;
            }
            if (!key.startsWith(head) || !key.endsWith(tail)) {
                continue;
            }
            String bare = key.substring(head.length(), key.length() - tail.length());
            if (WARNED_DELIMITED_KEYS.add(key)) {
                warn("Placeholder key '" + key + "' was declared with its delimiters, so it is"
                        + " registered exactly like that and will never match. Declare it as '" + bare
                        + "' and write " + Closures.DOLLAR_CURLY.quote(bare) + " in the text.");
            }
            return;
        }
    }

    // The plugin's own log adapter when there is one, JUL otherwise: this warning has no plugin to
    // attribute itself to, and it must still be heard on a runtime with no platform installed.
    private static void warn(String message) {
        try {
            EverNifeCore.getLog().warning(message);
        } catch (Throwable noPluginRuntime) {
            Logger.getLogger("EverNifeCore").log(Level.WARNING, message);
        }
    }
}
