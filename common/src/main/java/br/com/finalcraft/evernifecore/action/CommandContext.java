package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** A player about to run a command ({@link EventSource#COMMAND}): the label typed and every name of that command. */
public final class CommandContext extends ActionContext {

    private final String commandLine;
    private final Set<String> names;

    /**
     * @param position    where the player stands
     * @param nativeEvent the platform's event; null when there is none
     * @param commandLine what the player typed, with or without the leading {@code /}
     * @param knownAs     the command's own name and aliases, as the platform knows it; empty for a command it does not know
     */
    public CommandContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, String commandLine, Collection<String> knownAs) {
        super(EventSource.COMMAND, position, actor, nativeEvent);
        this.commandLine = commandLine;
        Set<String> all = new LinkedHashSet<>();
        all.add(label(commandLine));
        for (String name : knownAs) {
            all.add(label(name));
        }
        this.names = Collections.unmodifiableSet(all);
    }

    /** What the player typed, as typed. */
    public String getCommandLine() {
        return commandLine;
    }

    /** The label typed and the command's name and aliases, each as {@link #label} gives it. */
    public Set<String> getNames() {
        return names;
    }

    /** The command's label in {@code commandLine}: first word, lowercased, without {@code /} and without a {@code plugin:} prefix. */
    public static String label(String commandLine) {
        String text = commandLine.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int space = text.indexOf(' ');
        if (space >= 0) {
            text = text.substring(0, space);
        }
        int colon = text.lastIndexOf(':');   //essentials:home and home are the same command
        return text.substring(colon + 1).toLowerCase(Locale.ROOT);
    }
}
