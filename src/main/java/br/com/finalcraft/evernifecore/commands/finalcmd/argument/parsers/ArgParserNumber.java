package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.evernifecore.util.FCMathUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserNumber extends ArgParser<Number> {

    protected @Nullable Tuple<Double, Double> boundaries = null; //NotNull if the argument is bounded to two values
    protected @Nullable List<Double> possibilities = null; //NotNull if the argument must be one of the list
    protected final boolean isInteger;

    public ArgParserNumber(ArgInfo argInfo) {
        super(argInfo);

        isInteger = argInfo.getArgumentType().equals(Integer.class);

        String context = argInfo.getArgData().getContext();

        if (!context.isEmpty()){
            if (context.contains(":")){
                this.boundaries = ArgsParserUtil.parseNumericContextInterval(context);
            }else if (context.contains("|")){
                this.possibilities = ArgsParserUtil.parseNumericContextSelectional(context);
            }
        }
    }

    @Override
    public Number parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {
        Number number;
        //We cannot use a ternary operator here because of NPE caused by boxingAndUnboxing of values.
        if (isInteger) {
            number = argumento.getInteger();
            if (number == null){
                Long numberAsLong = argumento.getLong();
                //If the input argument is higher than Integer.MAX_VALUE, cast it to Integer.MAX_VALUE!
                if (numberAsLong != null && numberAsLong >= Integer.MAX_VALUE){
                    number = Integer.MAX_VALUE;
                }
                //If the input argument is lower than Integer.MIN_VALUE, cast it to Integer.MIN_VALUE!
                if (numberAsLong != null && numberAsLong <= Integer.MIN_VALUE){
                    number = Integer.MIN_VALUE;
                }
            }
        }else {
            number = argumento.getDouble();
        }

        if (number == null){
            if (this.argInfo.isRequired()){
                if (isInteger) {
                    FCMessageUtil.needsToBeInteger(sender, argumento.toString());
                }else {
                    FCMessageUtil.needsToBeDouble(sender, argumento.toString());
                }
                throw new ArgParseException();
            }
            return null;
        }

        if (boundaries != null){
            NumberWrapper value = NumberWrapper.of(number);

            if (!value.isBoundedLower(boundaries.getLeft())){
                FCMessageUtil.notBoundedLower(sender, value.get(), boundaries.getLeft());
                throw new ArgParseException();
            }

            if (!value.isBoundedUpper(boundaries.getRight())){
                FCMessageUtil.notBoundedUpper(sender, value.get(), boundaries.getRight());
                throw new ArgParseException();
            }

            return number;
        }

        if (possibilities != null){

            for (Double possibility : possibilities) {
                if (possibility.equals(number)){
                    return number;
                }
            }

            FCMessageUtil.notWithinPossibilities(sender, NumberWrapper.of(number).toString(), possibilities);
            throw new ArgParseException();
        }

        return number;
    }

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {
        if (possibilities != null){

            return possibilities.stream()
                    .map(aDouble -> FCMathUtil.toString(aDouble))
                    .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

        }

        return ImmutableList.of();
    }
}
