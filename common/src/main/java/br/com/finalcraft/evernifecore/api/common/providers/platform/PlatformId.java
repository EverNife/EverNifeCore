package br.com.finalcraft.evernifecore.api.common.providers.platform;

/**
 * The identity tags {@link IPlatform#getPlatformProviderId()} is known to return. They are persisted
 * inside account rows, so a value here can never change - only new ones be added.
 *
 * <p>Constants rather than an enum on purpose: the set is OPEN. The test engine already answers
 * {@code "test"}, and a future loader would bring its own tag, so a closed type would need an
 * {@code UNKNOWN} member and a defensive {@code valueOf} at every read.</p>
 *
 * <p>These are a SUBSET of the provider tags an account can carry: an account also links to external
 * identities such as {@code "discord"} or {@code "google"}, which no platform ever reports. A tag
 * missing from here is not an invalid account provider - it is a non-platform one.</p>
 */
public final class PlatformId {

    public static final String MINECRAFT = "minecraft";
    public static final String HYTALE = "hytale";

    private PlatformId() {
    }

}
