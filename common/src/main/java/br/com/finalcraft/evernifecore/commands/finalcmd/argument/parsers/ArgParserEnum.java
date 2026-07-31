package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

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
                    .map(e -> StringUtils.capitalize(((Enum) e).name().toLowerCase()))
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

        // Populate the lookup map for every enum constant selected by the possibilities
        // (works for both the implicit full-enum context and an explicit subset context)
        for (String possibility : possibilities) {
            Enum<?> constant = constantNamed(argInfo, possibility);
            if (constant == null){
                //An option that names no constant can never be satisfied: the argument would refuse every
                //token while offering this one, and only the boot knows it is a typo
                throw new ArgMountException("The context option [" + possibility + "] of the argument [" + argInfo.getArgData().getName() + "] " +
                        "is not a constant of " + argInfo.getArgumentType().getSimpleName() + " - no token could ever satisfy it. " +
                        "Valid constants: " + constantNamesOf(argInfo) + ".");
            }
            enumMap.put(possibility.toLowerCase(), constant);
        }
    }

    private static @Nullable Enum<?> constantNamed(ArgInfo argInfo, String wanted) {
        for (Object e : argInfo.getArgumentType().getEnumConstants()) {
            Enum<?> enumConstant = (Enum<?>) e;
            if (enumConstant.name().equalsIgnoreCase(wanted)){
                return enumConstant;
            }
        }
        return null;
    }

    private static String constantNamesOf(ArgInfo argInfo) {
        return Arrays.stream(argInfo.getArgumentType().getEnumConstants())
                .map(e -> ((Enum<?>) e).name())
                .collect(Collectors.joining(", "));
    }

    @Override
    public ParseResult<Enum> parse(@Nonnull ParseCall call) {
        Enum constant = enumMap.get(call.getArgumento().toLowerCase());

        return constant == null
                ? unrecognized(FCMessageUtil.NOT_WITHIN_POSSIBILITIES
                        .addPlaceholder("value", call.getArgumento().toString())
                        .addPlaceholder("possibilities", FCMessageUtil.possibilitiesText(possibilities)))
                : ParseResult.of(constant);
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return possibilities.stream()
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
