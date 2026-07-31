package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserBoolean extends ArgParser<Boolean> {

    protected List<String> possibilities;
    /** Whether the pair came from a {@code context()} the developer wrote, or from the built-in vocabulary. */
    protected final boolean declaredContext;

    public ArgParserBoolean(ArgInfo argInfo) {
        super(argInfo);

        this.declaredContext = !argInfo.getArgData().getContext().isEmpty();

        //If context is empty, take the name for it
        String context = declaredContext
                ? argInfo.getArgData().getContext()
                : argInfo.getArgData().getName();

        possibilities = ArgsParserUtil.parseStringContextSelectional(context);

        if (possibilities.size() != 2 && !declaredContext){
            possibilities = Arrays.asList("true","false");
        }

        //Fail fast at command registration instead of an IndexOutOfBounds at dispatch time
        Validate.isTrue(possibilities.size() == 2, "A custom-context ArgParserBoolean must have exactly 2 options! [context=='" + argInfo.getArgData().getContext() + "']");

        if (declaredContext){
            refuseInvertedPolarity(argInfo);
        }
    }

    /**
     * A declared pair reads by POSITION - first option true, second false - so a pair whose words already
     * mean the opposite to every player is a trap, not a choice: nobody types {@code off} expecting true.
     * The declaration is refused at boot rather than quietly reading backwards at dispatch.
     */
    private void refuseInvertedPolarity(ArgInfo argInfo) {
        for (int position = 0; position < possibilities.size(); position++) {
            String option = possibilities.get(position);
            Boolean spokenMeaning = new Argumento(option).getBoolean();
            boolean positionalMeaning = position == 0;

            if (spokenMeaning != null && spokenMeaning != positionalMeaning){
                throw new ArgMountException("The context [" + argInfo.getArgData().getContext() + "] of the Boolean argument [" + argInfo.getArgData().getName() + "] " +
                        "puts [" + option + "] where a " + positionalMeaning + " goes, and [" + option + "] already means " + spokenMeaning + " to everyone. " +
                        "The first option is the true one - swap the two sides.");
            }
        }
    }

    /**
     * A declared pair IS the truth table: the built-in vocabulary ({@code yes}, {@code sim}, {@code n}...)
     * only answers when nothing else was declared, so {@code sim} stops being accepted by a
     * {@code [buy|sell]} argument whose own refusal message never offered it.
     */
    @Override
    public ParseResult<Boolean> parse(@Nonnull ParseCall call) {
        Boolean bool = null;

        if (call.getArgumento().equalsIgnoreCase(possibilities.get(0))){
            bool = Boolean.TRUE;
        }else if (call.getArgumento().equalsIgnoreCase(possibilities.get(1))){
            bool = Boolean.FALSE;
        }else if (!declaredContext){
            bool = call.getArgumento().getBoolean();
        }

        return bool == null
                ? unrecognized(FCMessageUtil.NOT_WITHIN_POSSIBILITIES
                        .addPlaceholder("value", call.getArgumento().toString())
                        .addPlaceholder("possibilities", FCMessageUtil.possibilitiesText(possibilities)))
                : ParseResult.of(bool);
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
