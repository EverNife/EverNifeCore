package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.pageviewer.PageVisualization;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;

import java.util.List;

public class ArgParserPageVisualization extends ArgParser<PageVisualization> {

    protected final ArgParserNumber argParserNumber;

    public ArgParserPageVisualization(ArgInfo argInfo) {
        super(argInfo);
        if (argInfo.getArgData().getContext().isEmpty()){
            argInfo.getArgData().setContext("[1:*]");//By default, the context start at 1 and goes to infinity
        }
        this.argParserNumber = new ArgParserNumber(argInfo.deriveFor(Integer.class));
    }

    @Override
    public ParseResult<PageVisualization> parse(@Nonnull ParseCall call) {
        Argumento argumento = call.getArgumento();

        if (argumento.equalsIgnoreCase("all") && call.getSender().hasPermission(PermissionNodes.EVERNIFECORE_PAGEVIEWER_ALL)){
            return ParseResult.of(new PageVisualization(0, 0, true));
        }

        //Whether the token even LOOKS like an interval is decided by the token alone, before any
        //permission is consulted - otherwise '-5' meant "empty page to page 5" for whoever holds the
        //interval permission and "below the minimum" for everybody else, two answers to one typo
        int separator = separatorOfAnInterval(argumento.toString());
        if (separator > 0 && call.getSender().hasPermission(PermissionNodes.EVERNIFECORE_PAGEVIEWER_INTERVAL)){
            ParseResult<Integer> left = pageOf(call, argumento.toString().substring(0, separator));
            if (!left.hasValue()) return left.retype();

            ParseResult<Integer> right = pageOf(call, argumento.toString().substring(separator + 1));
            if (!right.hasValue()) return right.retype();

            return ParseResult.of(new PageVisualization(
                    Math.min(left.getValue(), right.getValue()),
                    Math.max(left.getValue(), right.getValue()),
                    false));
        }

        return argParserNumber.parse(call)
                .map(page -> new PageVisualization(page.intValue(), page.intValue(), false));
    }

    /**
     * Where {@code a-b} splits, or -1 when the token is not that shape. A leading dash is part of a
     * negative number rather than a separator, and there has to be exactly one separator with something
     * on both sides - the same "two halves and no more" rule the old {@code split("-")} length check had.
     */
    private static int separatorOfAnInterval(String raw) {
        int separator = raw.indexOf('-', 1);
        if (separator < 0 || separator == raw.length() - 1){
            return -1;
        }
        return raw.indexOf('-', separator + 1) < 0 ? separator : -1;
    }

    /**
     * Half of an interval: unlike the whole argument, neither side of {@code 1-5} may be missing, so a
     * side that does not convert is a refusal even when the argument itself is optional.
     */
    private ParseResult<Integer> pageOf(ParseCall call, String token) {
        return argParserNumber.parse(call.withArgumento(new Argumento(token)))
                .map(Number::intValue)
                .asDenied();
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return ImmutableList.of();

    }
}
