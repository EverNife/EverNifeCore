package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;

import java.util.LinkedHashMap;
import java.util.Optional;

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

        LinkedHashMap<String, String> contextKeyMap = new LinkedHashMap<>();
        if (context.trim().isEmpty()){
            return new ArgContextResult(contextKeyMap); //no context at all: every key simply absent
        }

        for (String rule : context.split("\\|")) {
            String[] pair = rule.split("=", 2);
            String key = pair[0].trim().toLowerCase();

            if (key.isEmpty()){
                throw new ArgMountException("The context entry [" + rule + "] of [" + context + "] names no key. " +
                        "Write 'key' for a bare switch, or 'key=value'.");
            }
            if (pair.length > 1 && pair[1].trim().isEmpty()){
                throw new ArgMountException("The context entry [" + rule + "] of [" + context + "] has an empty value after the '='. " +
                        "Write 'key' when the switch alone is what you mean, or give it a value.");
            }

            contextKeyMap.putIfAbsent(key, pair.length > 1 ? pair[1] : "true"); //a repeated key keeps the first
        }

        return new ArgContextResult(contextKeyMap);
    }

    public <O> Optional<O> get(ArgContextExtractor<O> extractor) {
        String contextKey = contextKeyMap.get(extractor.getId());
        if (contextKey == null) return Optional.empty();
        return Optional.ofNullable(extractor.getExtractor().apply(contextKey));
    }

}
