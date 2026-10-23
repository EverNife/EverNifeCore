package br.com.finalcraft.evernifecore.consolefilter;

/**
 * Decides which console lines are hidden. The core asks the one registered on {@link ECConsoleFilter}
 * for every line the server prints.
 *
 * <p>It runs inside the logging pipeline, on whichever thread logged, once per line: it must be
 * thread-safe, answer from memory, and never log - a log call here re-enters the pipeline it is
 * filtering.</p>
 */
@FunctionalInterface
public interface IConsoleFilterProvider {

    /**
     * The core's own answer while no plugin replaces it: hides the console echo of the two commands
     * the core issues on a player's behalf, {@code /ecdcmd} (a clicked dynamic command) and
     * {@code /ecpage} (a page turn), which would otherwise print one line per click.
     */
    IConsoleFilterProvider CORE_COMMANDS = message -> {
        String[] split = message.split(" ");
        return split.length >= 5 && (split[4].equals("/ecdcmd") || split[4].equals("/ecpage"));
    };

    /** Whether {@code message}, the formatted text of one console line, is hidden. */
    boolean isHidden(String message);
}
