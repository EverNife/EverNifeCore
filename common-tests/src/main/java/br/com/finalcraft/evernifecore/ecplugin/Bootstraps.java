package br.com.finalcraft.evernifecore.ecplugin;

/**
 * The published bootstrap instances a test leaves behind.
 *
 * <p>It lives in the bootstrap's own package because that is the only place the publication can be
 * reached from: production fills and empties an {@link ECBootstrap} through the plugin lifecycle
 * alone, and a test double that is built but never shut down has no lifecycle to empty it.</p>
 */
public final class Bootstraps {

    private Bootstraps() {
    }

    /**
     * Empties every {@link ECBootstrap}, so no test double is still handed out by the accessor of the
     * plugin it stood in for. A test that builds one owes this call.
     */
    public static void forgetAll() {
        ECBootstrap.forgetAll();
    }
}
