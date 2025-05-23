package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.Validate;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserString extends ArgParser<String> {

    protected final List<String> possibilities;

    public ArgParserString(ArgInfo argInfo) {
        super(argInfo);

        //If context is empty, take the name for it
        String context = argInfo.getArgData().getContext().isEmpty()
                ? argInfo.getArgData().getName()
                : argInfo.getArgData().getContext();

        possibilities = ImmutableList.copyOf(ArgsParserUtil.parseStringContextSelectional(context));

        Validate.isTrue(possibilities.size() > 0, "Can't create a ArgParserString without at least one option! [context=='" + context + "']");
    }

    @Override
    public String parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {

        if (possibilities.size() == 1){
            return argumento.isEmpty() ? null : argumento.toString();
        }

        for (String option : possibilities) {
            if (option.equalsIgnoreCase(argumento.toString())){
                return option;
            }
        }

        if (argInfo.isRequired()){
            FCMessageUtil.notWithinPossibilities(sender, argumento.toString(), possibilities);
            throw new ArgParseException();
        }

        return null;
    }

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
