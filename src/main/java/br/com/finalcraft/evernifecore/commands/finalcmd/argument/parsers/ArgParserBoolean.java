package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserBoolean extends ArgParser<Boolean> {

    protected List<String> possibilities;

    public ArgParserBoolean(ArgInfo argInfo) {
        super(argInfo);

        //If context is empty, take the name for it
        String context = argInfo.getArgData().getContext().isEmpty()
                ? argInfo.getArgData().getName()
                : argInfo.getArgData().getContext();

        possibilities = ArgsParserUtil.parseStringContextSelectional(context);

        if (possibilities.size() != 2 && argInfo.getArgData().getContext().isEmpty()){
            possibilities = Arrays.asList("true","false");
        }
    }

    @Override
    public Boolean parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {

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
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
