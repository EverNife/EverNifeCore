package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;

/**
 * Context class used to move information between
 * ArgParsers and ArgParserContextual
 */
public class ArgParserCommandContext {
    private final HelpContext helpContext;
    private final HelpLine helpLine;
    private final String label;
    private final MultiArgumentos argumentos;
    private final LinkedHashMap<Class, Object> parsedArgs;
    private final LinkedHashMap<Class, Object> parsedContext;
    private final boolean flagValue;
    private boolean shouldMoveArgIndex = true;

    /**
     * @param flagValue true when this context is parsing a flag's value (or its def()), so an
     * {@link ArgParser} can customize its behavior via {@link #isFlag()}; false for a positional
     * argument or a contextual argument.
     */
    public ArgParserCommandContext(HelpContext helpContext, HelpLine helpLine, String label, MultiArgumentos argumentos, LinkedHashMap<Class, Object> parsedArgs, LinkedHashMap<Class, Object> parsedContext, boolean flagValue) {
        this.helpContext = helpContext;
        this.helpLine = helpLine;
        this.label = label;
        this.argumentos = argumentos;
        this.parsedArgs = parsedArgs;
        this.parsedContext = parsedContext;
        this.flagValue = flagValue;
    }

    public boolean isFlag() {
        return flagValue;
    }

    public <T> @Nullable T getPreviouslyParsedArg(Class<T> clazz) {
        return (T) parsedArgs.get(clazz);
    }

    public LinkedHashMap<Class, Object> getParsedArgs() {
        return parsedArgs;
    }

    public LinkedHashMap<Class, Object> getParsedContext() {
        return parsedContext;
    }

    public HelpContext getHelpContext() {
        return helpContext;
    }

    public HelpLine getHelpLine() {
        return helpLine;
    }

    public String getLabel() {
        return label;
    }

    public MultiArgumentos getArgumentos() {
        return argumentos;
    }

    public boolean shouldMoveArgIndex() {
        return shouldMoveArgIndex;
    }

    public void setShouldMoveArgIndex(boolean shouldMoveArgIndex) {
        this.shouldMoveArgIndex = shouldMoveArgIndex;
    }

}
