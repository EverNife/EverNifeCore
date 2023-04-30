package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;

public class ArgContextResult {

    private final LinkedHashMap<String, String> contextKeyMap;

    public ArgContextResult(LinkedHashMap<String, String> contextKeyMap) {
        this.contextKeyMap = contextKeyMap;
    }

    /**
     * This function will split a string with the following format:
     *    "<[key1=value1|key2=value2|key3=value3]>"
     *
     *    into a LinkedHashMap with the following format:
     *       key1 -> value1
     *       key2 -> value2
     *       key3 -> value3
     */
    public static ArgContextResult parseFrom(String context) {
        context = ArgRequirementType.stripBrackets(context); //Remove Requirement Type

        LinkedHashMap<String, String> contextKeyMap = Arrays.stream(context.split("\\|"))
                .map(rule -> rule.split("="))
                .collect(Collectors.toMap(pair -> pair[0].toLowerCase(), pair -> {
                    if (pair.length > 1) {
                        return pair[1];
                    } else {
                        return "true";
                    }
                }, (v1, v2) -> v1, LinkedHashMap::new));

        return new ArgContextResult(contextKeyMap);
    }

    public <O> Optional<O> get(ArgContextExtractor<O> extractor) {
        String contextKey = contextKeyMap.get(extractor.getId());
        if (contextKey == null) return null;
        return Optional.ofNullable(extractor.getExtractor().apply(contextKey));
    }

}
