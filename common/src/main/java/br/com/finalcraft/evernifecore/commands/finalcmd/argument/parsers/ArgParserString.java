package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserString extends ArgParser<String> {

    protected final List<String> possibilities;
    /** Whether the options came from a {@code context()} the developer wrote, or from the argument's own name. */
    protected final boolean declaredContext;

    public ArgParserString(ArgInfo argInfo) {
        super(argInfo);

        this.declaredContext = !argInfo.getArgData().getContext().isEmpty();

        //If context is empty, take the name for it
        String context = declaredContext
                ? argInfo.getArgData().getContext()
                : argInfo.getArgData().getName();

        possibilities = ImmutableList.copyOf(ArgsParserUtil.parseStringContextSelectional(context));

        Validate.isTrue(possibilities.size() > 0, "Can't create a ArgParserString without at least one option! [context=='" + context + "']");
    }

    /**
     * A declared {@code context()} is a choice, always - including a choice of one. Only a single option
     * that came from the argument's own NAME is free text: {@code <player>} names the slot, it does not
     * restrict it, while {@code context = "admin"} was typed to mean exactly that word.
     */
    @Override
    public ParseResult<String> parse(@Nonnull ParseCall call) {
        if (!declaredContext && possibilities.size() == 1){
            return call.getArgumento().isEmpty()
                    ? notWithinPossibilities(call)
                    : ParseResult.of(call.getArgumento().toString());
        }

        for (String option : possibilities) {
            if (option.equalsIgnoreCase(call.getArgumento().toString())){
                return ParseResult.of(option); //the DECLARED spelling: what the method's own author wrote
            }
        }

        return notWithinPossibilities(call);
    }

    private ParseResult<String> notWithinPossibilities(ParseCall call) {
        return unrecognized(FCMessageUtil.NOT_WITHIN_POSSIBILITIES
                .addPlaceholder("value", call.getArgumento().toString())
                .addPlaceholder("possibilities", FCMessageUtil.possibilitiesText(possibilities)));
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
