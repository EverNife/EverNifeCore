package br.com.finalcraft.evernifecore.logger;

/**
 * The severity an {@link ILogAdapter} receives, ordered from least to most severe.
 *
 * <p>{@code common} knows nothing about the platform underneath it, and a level type is the one
 * place that leaks: a JUL level on the SPI would force every implementor - Hytale, Forge, a headless
 * harness - to speak a logging framework it may not use. Translating this enum into whatever the
 * server actually logs with is the adapter's job, and its alone.</p>
 */
public enum ECLogLevel {
    /**
     * Detail nobody needs until they do, emitted only while the plugin's {@code DebugMode} is on.
     * A platform whose console has no channel below info is expected to map it to info: dropping the
     * line would silently defeat the switch the operator just turned on.
     */
    DEBUG,
    INFO,
    WARNING,
    SEVERE
}
