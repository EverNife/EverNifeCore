package br.com.finalcraft.evernifecore.testing;

/**
 * What a strict platform double throws for a call the test never configured: its message names the builder call that
 * teaches the double the answer.
 *
 * <p>It stays an {@link UnsupportedOperationException}, the type the core reads as "this platform does not offer that".
 * Core code that only reports such a failure (a login claim, a subscriber) still fails the test: {@link ECoreTestWorld}
 * watches the global bus's exception handler for this type and fails its {@code close()} with the message.</p>
 */
public class PlatformNotConfiguredException extends UnsupportedOperationException {

    public PlatformNotConfiguredException(String message) {
        super(message);
    }

}
