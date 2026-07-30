package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The {@code @FinalCMD.Capture} of one node, resolved at registration: how many tokens the node eats
 * right after its own label, how to turn them into the node's context object, and what that object's
 * type is.
 * <p>
 * The token count is fixed, never conditional - {@code k} declared arguments are {@code k} consumed
 * tokens - which is what lets the traversal count tokens without parsing any of them.
 */
public final class CaptureBinding {

    private final Method method;
    private final CMDMethodInterpreter interpreter;
    private final List<ArgParser> argParsers;
    private final @Nullable Class<?> contextType;

    public CaptureBinding(@Nonnull Method method, @Nonnull CMDMethodInterpreter interpreter, @Nullable Class<?> contextType) {
        this.method = method;
        this.interpreter = interpreter;
        this.contextType = contextType;
        this.argParsers = Collections.unmodifiableList(new ArrayList<ArgParser>(interpreter.getCustomArguments().values()));
    }

    public Method getMethod() {
        return method;
    }

    public CMDMethodInterpreter getInterpreter() {
        return interpreter;
    }

    public List<ArgParser> getArgParsers() {
        return argParsers;
    }

    /** The type of the node's context object; null when the capture only declares flags ({@code void}). */
    public @Nullable Class<?> getContextType() {
        return contextType;
    }

    /** How many tokens this capture consumes - always exactly its declared {@code @Arg} count. */
    public int tokenWidth() {
        return argParsers.size();
    }

    /** The declared argument names, in order, for the usage line - {@code ["<server>", "<user>"]}. */
    public List<String> getArgNames() {
        List<String> names = new ArrayList<>();
        for (ArgParser parser : argParsers) {
            names.add(parser.getArgInfo().getArgData().getName());
        }
        return names;
    }
}
