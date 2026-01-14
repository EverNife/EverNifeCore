package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserArgumento extends ArgParser<Argumento> {

    protected final List<String> possibilities;

    public ArgParserArgumento(ArgInfo argInfo) {
        super(argInfo);

        //If context is empty, take the name for it
        String context = argInfo.getArgData().getContext().isEmpty()
                ? argInfo.getArgData().getName()
                : argInfo.getArgData().getContext();

        possibilities = ImmutableList.copyOf(ArgsParserUtil.parseStringContextSelectional(context));
    }

    @Override
    public Argumento parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {

        if (argInfo.isRequired() && argumento.isEmpty()){
            FCMessageUtil.notWithinPossibilities(sender, argumento.toString(), possibilities);
            throw new ArgParseException();
        }

        return argumento;
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
