package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.logger.ECLogFormat;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.everydatabase.log.StorageLogEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logging shared by the playerdata engines (controller, flush, quit lifecycle, transfer):
 * routes to the ECore logger, falling back to JUL on a pure JUnit runtime.
 */
final class PDLog {

    private PDLog() {
    }

    static void routeStorageLogEvent(StorageLogEvent event) {
        switch (event.level()) {
            case ERROR:
                severe(event.format());
                if (event.error() != null) event.error().printStackTrace();
                break;
            case WARN:
                warning(event.format());
                break;
            default:
                info(event.format());
                break;
        }
    }

    static void debug(String message, Object... args) {
        String formatted = ECLogFormat.format(message, args);
        try {
            EverNifeCore.getLog().debug(formatted);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData plugged in): debug is a no-op there
            Logger.getLogger("EverNifeCore").log(Level.FINE, formatted);
        }
    }

    static void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    static void warning(String message, Object... args) {
        log(Level.WARNING, message, args);
    }

    static void severe(String message, Object... args) {
        log(Level.SEVERE, message, args);
    }

    static void log(Level level, String message, Object... args) {
        String formatted = ECLogFormat.format(message, args);
        try {
            ECLogger logger = EverNifeCore.getLog();
            if (level == Level.SEVERE) {
                logger.severe(formatted);
            } else if (level == Level.WARNING) {
                logger.warning(formatted);
            } else {
                logger.info(formatted);
            }
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData plugged in): falls back to JUL
            Logger.getLogger("EverNifeCore").log(level, formatted);
        }
    }
}
