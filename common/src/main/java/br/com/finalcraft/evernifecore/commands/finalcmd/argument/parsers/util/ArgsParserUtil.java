package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.everylibs.util.FCInputReader;
import br.com.finalcraft.everylibs.commons.Tuple;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ArgsParserUtil {

    //Parse Contexts and return values -->
    //     [-2.5:10.75] --> return Tuple of -2.5 and 10.75
    //     [1.5:*] --> return Tuple of 1.5 and Double.MAX_VALUE
    //     [*:10] --> return Tuple of -Double.MAX_VALUE and 10
    public static @Nonnull Tuple<Double, Double> parseNumericContextInterval(@Nonnull String context){
        context = ArgRequirementType.stripBrackets(context); //Remove Requirement Type

        final String[] numbers = context.split(":", 2);
        Double baseNumber = FCInputReader.parseDouble(numbers[0]);
        Double limiarNumber = FCInputReader.parseDouble(numbers[1]);

        //A bare '*' means "no limit on this side", and which side it is on decides the sign - [*:10]
        //reads as "up to 10", which is how everybody writes it. '-*' and '+*' stay explicit.
        if (baseNumber == null && numbers[0].contains("*")) baseNumber = openEndedBound(numbers[0], -Double.MAX_VALUE);
        if (limiarNumber == null && numbers[1].contains("*")) limiarNumber = openEndedBound(numbers[1], Double.MAX_VALUE);

        if (baseNumber == null) throw new ArgMountException("Failed to parse base double value from [" + context + "] --> '" + numbers[0] + "'");
        if (limiarNumber == null) throw new ArgMountException("Failed to parse limiar double value from [" + context + "] --> '" + numbers[1] + "'");

        if (baseNumber > limiarNumber){
            throw new ArgMountException("The interval [" + context + "] has its floor (" + baseNumber + ") above its ceiling (" + limiarNumber + "), " +
                    "so no value could ever satisfy it - swap the two sides.");
        }

        return Tuple.of(baseNumber, limiarNumber);
    }

    /** An explicitly signed {@code -*}/{@code +*}, or the unlimited end of the side it was written on. */
    private static double openEndedBound(@Nonnull String written, double sideDefault){
        String trimmed = written.trim();
        if (trimmed.startsWith("-")) return -Double.MAX_VALUE;
        if (trimmed.startsWith("+")) return Double.MAX_VALUE;
        return sideDefault;
    }

    //Parse Contexts and return values -->
    //     [2.5|10|20] --> return HashSet containing 2.5,10,20
    public static @Nonnull List<Double> parseNumericContextSelectional(@Nonnull String context){
        context = ArgRequirementType.stripBrackets(context); //Remove Requirement Type

        List<Double> numbeers = new ArrayList<>();
        for (String number : context.split(Pattern.quote("|"))) {
            Double value = FCInputReader.parseDouble(number);
            if (value == null){
                throw new ArgMountException("Failed to parse double value from [" + number + "]");
            }
            numbeers.add(FCInputReader.parseDouble(number));
        }
        return numbeers;
    }

    /**
     * The options of a {@code a|b|c} context, exactly as they were written: {@code [Abra|cadAbrA]}
     * yields {@code Abra} and {@code cadAbrA}.
     * <p>
     * The casing is the declaration's, not noise - it is what a parser hands back once an option matches,
     * so the method receives the spelling its own author chose instead of a lowercased one. Matching
     * against what the sender typed stays case-insensitive, everywhere.
     */
    public static @Nonnull List<String> parseStringContextSelectional(@Nonnull String context){
        context = ArgRequirementType.stripBrackets(context); //Remove Requirement Type

        List<String> strings = new ArrayList<>();
        for (String string : context.split(Pattern.quote("|"))) {
            strings.add(string);
        }
        return strings;
    }

}
