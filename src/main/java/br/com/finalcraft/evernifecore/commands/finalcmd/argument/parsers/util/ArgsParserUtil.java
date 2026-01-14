package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.util;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.commons.Tuple;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ArgsParserUtil {

    //Parse Contexts and return values -->
    //     [-2.5:10.75] --> return Tuple of -2.5 and 10.75
    //     [1.5:*] --> return Tuple of 1 and Double.MAX_VALUE
    public static @Nonnull Tuple<Double, Double> parseNumericContextInterval(@Nonnull String context){
        context = ArgRequirementType.stripBrackets(context); //Remove Requirement Type

        final String[] numbers = context.split(":", 2);
        Double baseNumber = FCInputReader.parseDouble(numbers[0]);
        Double limiarNumber = FCInputReader.parseDouble(numbers[1]);

        if (baseNumber == null && numbers[0].contains("*")) baseNumber = (numbers[0].startsWith("-") ? -1 : 1) * Double.MAX_VALUE;
        if (limiarNumber == null && numbers[1].contains("*")) limiarNumber = (numbers[1].startsWith("-") ? -1 : 1) * Double.MAX_VALUE;

        if (baseNumber == null) throw new ArgMountException("Failed to parse base double value from [" + context + "] --> '" + numbers[0] + "'");
        if (limiarNumber == null) throw new ArgMountException("Failed to parse limiar double value from [" + context + "] --> '" + numbers[1] + "'");

        return Tuple.of(baseNumber, limiarNumber);
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

    //Parse Contexts and return values -->
    //     [Abra|cadAbrA] --> return HashSet containing abra|cadabra
    public static @Nonnull List<String> parseStringContextSelectional(@Nonnull String context){
        context = ArgRequirementType.stripBrackets(context); //Remove Requirement Type

        List<String> strings = new ArrayList<>();
        for (String string : context.split(Pattern.quote("|"))) {
            strings.add(string.toLowerCase());
        }
        return strings;
    }

}
