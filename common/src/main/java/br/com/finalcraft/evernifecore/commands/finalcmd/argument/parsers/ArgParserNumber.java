package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util.ArgsParserUtil;
import br.com.finalcraft.everylibs.util.FCMathUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import br.com.finalcraft.everylibs.commons.Tuple;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArgParserNumber extends ArgParser<Number> {

    /**
     * What each integral type can hold. A token outside its range is REFUSED with the range it missed -
     * it converted fine, it just does not fit, and a silent clamp to MAX_VALUE was an answer nobody
     * asked for and nobody could see.
     */
    private static final Map<Class<?>, Tuple<Long, Long>> INTEGRAL_RANGES = integralRanges();

    protected @Nullable Tuple<Double, Double> boundaries = null; //NotNull if the argument is bounded to two values
    protected @Nullable List<Double> possibilities = null; //NotNull if the argument must be one of the list
    /** The type the parameter declared - what this parser hands back, never a guess that {@code invoke} then rejects. */
    protected final Class<?> numberType;
    protected final boolean isInteger;

    public ArgParserNumber(ArgInfo argInfo) {
        super(argInfo);

        this.numberType = argInfo.getArgumentType();
        this.isInteger = INTEGRAL_RANGES.containsKey(numberType);

        String context = argInfo.getArgData().getContext();

        if (!context.isEmpty()){
            if (context.contains(":")){
                this.boundaries = ArgsParserUtil.parseNumericContextInterval(context);
                refuseFractionalBoundOnAnIntegralType(argInfo, context);
            }else if (context.contains("|")){
                this.possibilities = ArgsParserUtil.parseNumericContextSelectional(context);
            }
        }
    }

    @Override
    public ParseResult<Number> parse(@Nonnull ParseCall call) {
        Argumento argumento = call.getArgumento();

        ParseResult<Number> converted = convert(argumento);
        if (!converted.hasValue()){
            return converted;
        }
        Number number = converted.getValue();

        //Out of range or off the list is a REFUSAL of a value that converted fine, so it is fatal even
        //on an optional argument - which was already true, and is now written down
        if (boundaries != null){
            double value = number.doubleValue();

            if (value < boundaries.getLeft()){
                return denied(FCMessageUtil.NOT_BOUNDED_LOWER
                        .addPlaceholder("number", NumberWrapper.of(number))
                        .addPlaceholder("min", NumberWrapper.of(boundaries.getLeft())));
            }

            if (value > boundaries.getRight()){
                return denied(FCMessageUtil.NOT_BOUNDED_UPPER
                        .addPlaceholder("number", NumberWrapper.of(number))
                        .addPlaceholder("max", NumberWrapper.of(boundaries.getRight())));
            }

            return ParseResult.of(number);
        }

        if (possibilities != null){
            //The list is always read as Double, while an Integer argument produces an Integer: only a
            //NumberWrapper compares the two by their numeric value instead of by their boxed type
            NumberWrapper value = NumberWrapper.of(number);

            for (Double possibility : possibilities) {
                if (value.equals(NumberWrapper.of(possibility))){
                    return ParseResult.of(number);
                }
            }

            return denied(FCMessageUtil.NOT_WITHIN_POSSIBILITIES
                    .addPlaceholder("value", NumberWrapper.of(number).toString())
                    .addPlaceholder("possibilities", FCMessageUtil.possibilitiesText(possibilities)));
        }
        return ParseResult.of(number);
    }

    /**
     * The token as the type the parameter declared, so {@code method.invoke} never sees a Double where a
     * Float was asked for. A value an integral type cannot hold is refused rather than clamped.
     */
    private ParseResult<Number> convert(Argumento argumento) {
        Tuple<Long, Long> range = INTEGRAL_RANGES.get(numberType);
        if (range == null){
            Double asDouble = argumento.getDouble();
            if (asDouble == null){
                return unrecognized(FCMessageUtil.NEEDS_TO_BE_DOUBLE.addPlaceholder("argumento", argumento.toString()));
            }
            return ParseResult.<Number>of(numberType == Float.class ? (Number) Float.valueOf(asDouble.floatValue()) : asDouble);
        }

        Long asLong = argumento.getLong();
        if (asLong == null){
            return unrecognized(FCMessageUtil.NEEDS_TO_BE_INTEGER.addPlaceholder("argumento", argumento.toString()));
        }

        if (asLong < range.getLeft()){
            return denied(FCMessageUtil.NOT_BOUNDED_LOWER
                    .addPlaceholder("number", NumberWrapper.of(asLong))
                    .addPlaceholder("min", NumberWrapper.of(range.getLeft())));
        }
        if (asLong > range.getRight()){
            return denied(FCMessageUtil.NOT_BOUNDED_UPPER
                    .addPlaceholder("number", NumberWrapper.of(asLong))
                    .addPlaceholder("max", NumberWrapper.of(range.getRight())));
        }

        return ParseResult.<Number>of(narrowed(asLong));
    }

    private Number narrowed(long value) {
        if (numberType == Integer.class){
            return Integer.valueOf((int) value);
        }
        if (numberType == Short.class){
            return Short.valueOf((short) value);
        }
        if (numberType == Byte.class){
            return Byte.valueOf((byte) value);
        }
        return Long.valueOf(value);
    }

    /**
     * A fractional bound on an integral argument is a typo, not a rule: {@code [0:2.5]} can only ever
     * mean {@code [0:2]}, and reading it as one silently was the whole problem.
     */
    private void refuseFractionalBoundOnAnIntegralType(ArgInfo argInfo, String context) {
        if (!isInteger){
            return;
        }
        for (Double bound : new Double[]{boundaries.getLeft(), boundaries.getRight()}) {
            if (bound != Math.floor(bound)){
                throw new ArgMountException("The context [" + context + "] of the argument [" + argInfo.getArgData().getName() + "] " +
                        "bounds a " + numberType.getSimpleName() + " with the fractional value [" + bound + "], which no whole number can sit on. " +
                        "Write a whole bound, or declare the argument as Double.");
            }
        }
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        if (possibilities != null){

            return possibilities.stream()
                    .map(aDouble -> FCMathUtil.toString(aDouble))
                    .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

        }

        return new ArrayList<>();
    }

    private static Map<Class<?>, Tuple<Long, Long>> integralRanges() {
        Map<Class<?>, Tuple<Long, Long>> ranges = new LinkedHashMap<>();
        ranges.put(Integer.class, Tuple.of((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE));
        ranges.put(Long.class, Tuple.of(Long.MIN_VALUE, Long.MAX_VALUE));
        ranges.put(Short.class, Tuple.of((long) Short.MIN_VALUE, (long) Short.MAX_VALUE));
        ranges.put(Byte.class, Tuple.of((long) Byte.MIN_VALUE, (long) Byte.MAX_VALUE));
        return Collections.unmodifiableMap(ranges);
    }
}
