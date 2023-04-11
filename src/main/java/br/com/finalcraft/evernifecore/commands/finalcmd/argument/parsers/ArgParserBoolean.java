package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.Validate;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserBoolean extends ArgParser<Boolean> {

    private final List<String> possibilities;

    public ArgParserBoolean(ArgInfo argInfo) {
        super(argInfo);

        //If context is empty, take the name for it
        String context = !argInfo.getArgData().getName().isEmpty() ? argInfo.getArgData().getName() : argInfo.getArgData().getContext();

        possibilities = ImmutableList.copyOf(ArgsParserUtil.parseStringContextSelectional(context));

        Validate.isTrue(possibilities.size() == 2, "Can't create a ArgParserBoolean without exactly two options! [context=='" + context + "']");
    }

    @Override
    public Boolean parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {

        Boolean bool = argumento.getBoolean();

        if (bool == null){
            //First lets try possibilities, maybe it's a custom boolean not a simple "yes/no"
            if (argumento.equalsIgnoreCase(possibilities.get(0))){
                bool = true;
            }else if (argumento.equalsIgnoreCase(possibilities.get(1))){
                bool = false;
            }
        }

        if (argInfo.isRequired() && bool == null){
            FCMessageUtil.notWithinPossibilities(sender, argumento.toString(), possibilities);
            throw new ArgParseException();
        }

        return bool;
    }

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
