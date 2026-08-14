package br.com.finalcraft.evernifecore.logger;

/**
 * Where a formatted log line leaves {@code common} and reaches the server's own logger.
 *
 * <p>One method, because every verb of {@link ECLogger} already arrives here as an
 * {@link ECLogLevel}: an adapter maps the four levels once and is done, instead of repeating the
 * same call shape per severity.</p>
 */
public interface ILogAdapter {

    void log(ECLogLevel level, String message);

}
