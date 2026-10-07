package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a set of named values typed in either of two grammars: a JSON object ({@code {"revert":"spawn"}}), when the
 * text starts with {@code {}, or {@code key=value} pairs separated by spaces, where a double-quoted value keeps its
 * spaces ({@code revert=spawn note="two words"}). A pair's value is always text.
 */
public class ArgParserObjectNode extends ArgParser<ObjectNode> {

    private static final ObjectMapper JSON = new ObjectMapper();

    public ArgParserObjectNode(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §c'§e${input}§c' is neither key=value pairs nor a JSON object. §3Write it as §ekey=value key2=\"two words\"§3 or as §e{\"key\":\"value\"}§3.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §c'§e${input}§c' não é nem pares chave=valor nem um objeto JSON. §3Escreva como §echave=valor chave2=\"duas palavras\"§3 ou como §e{\"chave\":\"valor\"}§3.")
    public static LocaleMessage NOT_KEY_VALUES_NOR_JSON;

    @Override
    public ParseResult<ObjectNode> parse(@Nonnull ParseCall call) {
        String input = call.getArgumento().toString();
        try {
            return ParseResult.of(parse(input));
        } catch (IllegalArgumentException refused) {
            return ParseResult.denied(NOT_KEY_VALUES_NOR_JSON.addPlaceholder("input", input));
        }
    }

    /**
     * The object {@code text} spells; blank text is an empty object.
     *
     * @throws IllegalArgumentException naming both grammars when the text is malformed JSON, JSON that is not an
     * object, or a token without {@code =}
     */
    public static ObjectNode parse(String text) throws IllegalArgumentException {
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            JsonNode tree;
            try {
                tree = JSON.readTree(trimmed);
            } catch (JsonProcessingException malformed) {
                throw new IllegalArgumentException("'" + trimmed + "' starts like JSON but does not parse (" + malformed.getOriginalMessage()
                        + ") - write a JSON object {\"key\":\"value\"}, or key=value pairs.", malformed);
            }
            if (!(tree instanceof ObjectNode)) {
                throw new IllegalArgumentException("'" + trimmed + "' is JSON but not an object - write a JSON object {\"key\":\"value\"}, or key=value pairs.");
            }
            return (ObjectNode) tree;
        }

        ObjectNode object = JsonNodeFactory.instance.objectNode();
        for (String pair : splitPairs(trimmed)) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("'" + pair + "' is not key=value - write each entry as key=value (quote a value with spaces), "
                        + "or the whole thing as a JSON object {\"key\":\"value\"}.");
            }
            object.put(pair.substring(0, equals), pair.substring(equals + 1));
        }
        return object;
    }

    /** Splits on whitespace outside double quotes, dropping the quotes: {@code a=1 b="x y"} gives {@code a=1}, {@code b=x y}. */
    private static List<String> splitPairs(String text) {
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    pairs.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            pairs.add(current.toString());
        }
        return pairs;
    }
}
