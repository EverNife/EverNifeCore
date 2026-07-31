package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserArgumento extends ArgParser<Argumento> {

    protected final List<String> possibilities;
    /** Whether the options came from a {@code context()} the developer wrote, or from the argument's own name. */
    protected final boolean declaredContext;

    public ArgParserArgumento(ArgInfo argInfo) {
        super(argInfo);

        this.declaredContext = !argInfo.getArgData().getContext().isEmpty();

        //If context is empty, take the name for it
        String context = declaredContext
                ? argInfo.getArgData().getContext()
                : argInfo.getArgData().getName();

        possibilities = ImmutableList.copyOf(ArgsParserUtil.parseStringContextSelectional(context));
    }

    /**
     * A declared {@code context()} is applied here exactly as it is on a String argument: the raw token
     * type is a convenience, not an exemption from what the declaration says. Without one, any token
     * goes - the options then only feed tab-complete.
     */
    @Override
    public ParseResult<Argumento> parse(@Nonnull ParseCall call) {
        if (call.getArgumento().isEmpty()){
            return notWithinPossibilities(call);
        }

        if (!declaredContext){
            return ParseResult.of(call.getArgumento());
        }

        for (String option : possibilities) {
            if (call.getArgumento().equalsIgnoreCase(option)){
                return ParseResult.of(new Argumento(option)); //the DECLARED spelling, like every other choice
            }
        }

        return notWithinPossibilities(call);
    }

    private ParseResult<Argumento> notWithinPossibilities(ParseCall call) {
        return unrecognized(FCMessageUtil.NOT_WITHIN_POSSIBILITIES
                .addPlaceholder("value", call.getArgumento().toString())
                .addPlaceholder("possibilities", FCMessageUtil.possibilitiesText(possibilities)));
    }

    /** An optional token nobody typed still arrives as an {@link Argumento} - an empty one, never null. */
    @Override
    public ParseResult<Argumento> absent(@Nonnull ParseCall call) {
        return ParseResult.of(call.getArgumento());
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
