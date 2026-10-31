package br.com.finalcraft.evernifecore.dialogue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;

/** The text leaves of a dialogue as the {@code LocaleOverlay} reads them: a string, a list of lines or a locale map. */
final class DialogueTexts {

    private DialogueTexts() {
    }

    static JsonNode of(String text) {
        return JsonNodeFactory.instance.textNode(Objects.requireNonNull(text, "text"));
    }

    static JsonNode of(Map<String, String> byLocale) {
        if (byLocale.isEmpty()) {
            throw new IllegalArgumentException("A text per locale needs at least one locale, e.g. {pt-BR: \"Olá\", en-US: \"Hello\"}.");
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        byLocale.forEach(node::put);
        return node;
    }

    static JsonNode of(JsonNode text) {
        if (text == null || text.isNull() || text.isMissingNode() || text.isObject() && text.size() == 0) {
            throw new IllegalArgumentException("A dialogue text is a string, a list of lines or a map of locale to text"
                    + " (e.g. {pt-BR: \"Olá\", en-US: \"Hello\"}) - this one is empty.");
        }
        return text.deepCopy();
    }
}
