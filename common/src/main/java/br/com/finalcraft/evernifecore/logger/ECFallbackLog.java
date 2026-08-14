package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The logger {@link EverNifeCore#getLog()} answers with while the core holds no plugin data - a bare
 * JUnit run, a static initializer that beat the bootstrap, a plugin that reached EverNifeCore before
 * it loaded. It needs no platform and no config, so it works from the very first class that loads.
 *
 * <p>Lines go to the JUL logger named {@value #LOGGER_NAME}, which is where an operator points a
 * {@code logging.properties} handler and where a test attaches one. {@code debug} maps to
 * {@link Level#FINE}: it is not gated by any {@code DebugMode} block, because there is no plugin to
 * own one - JUL's own level, INFO by default, is what keeps it quiet.</p>
 */
public final class ECFallbackLog {

    /** Name of the JUL logger every fallback line is published to. */
    public static final String LOGGER_NAME = "EverNifeCore";

    private static final ECLogger INSTANCE = new ECLogger(null, new JulAdapter());

    private ECFallbackLog() {
    }

    /** The single fallback instance - stateless, so there is no reason for a second one. */
    public static ECLogger get() {
        return INSTANCE;
    }

    private static final class JulAdapter implements ILogAdapter {

        @Override
        public void log(ECLogLevel level, String message) {
            Logger.getLogger(LOGGER_NAME).log(julLevelOf(level), message);
        }

        private static Level julLevelOf(ECLogLevel level) {
            switch (level) {
                case SEVERE:
                    return Level.SEVERE;
                case WARNING:
                    return Level.WARNING;
                case DEBUG:
                    return Level.FINE;
                default:
                    return Level.INFO;
            }
        }
    }
}
