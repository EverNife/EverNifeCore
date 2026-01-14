package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserEnum extends ArgParser<Enum> {

    protected final List<String> possibilities;
    protected final HashMap<String, Enum<?>> enumMap = new HashMap<>();

    public ArgParserEnum(ArgInfo argInfo) {
        super(argInfo);

        String context = argInfo.getArgData().getContext();

        if (context.isEmpty()){ //If context is empty, take the entire enum as possibility
            Object[] enumValues = argInfo.getArgumentType().getEnumConstants();
            if (enumValues.length > 50){
                EverNifeCore.getLog().warning("[ArgParserEnum] The ArgInfo [" + argInfo + "] has more than " + enumValues.length + " constants! This is Wrong! Don't use ArgParserEnum like this!");
                throw new IllegalArgumentException("The ArgParserEnum cannot have more than 50 possible values on its enum!");
            }
            // Transform the enum into something like     VALUE1|VALUE2|VALUE
            context = Arrays.stream(argInfo.getArgumentType().getEnumConstants())
                    .map(e -> {
                        Enum enumConstant = (Enum)e;
                        String nameLowercase = enumConstant.name().toLowerCase();
                        enumMap.put(nameLowercase, enumConstant);
                        return StringUtils.capitalize(nameLowercase);
                    })
                    .collect(Collectors.joining("|"));
        }

        if (argInfo.getArgData().getName().equals("<>")){
            argInfo.getArgData().setName("<" + context + ">");
        }
        if (argInfo.getArgData().getName().equals("[]")){
            argInfo.getArgData().setName("[" + context + "]");
        }

        possibilities = ImmutableList.copyOf(ArgsParserUtil.parseStringContextSelectional("<" + context + ">"));

        Validate.isTrue(possibilities.size() > 0, "Can't create a ArgParserEnum without at least one option! [context=='" + context + "']");
    }

    @Override
    public Enum parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {

        Enum result = enumMap.get(argumento.toLowerCase());

        if (result == null && argInfo.isRequired()){
            FCMessageUtil.notWithinPossibilities(sender, argumento.toString(), possibilities);
            throw new ArgParseException();
        }

        return result;
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
