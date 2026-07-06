package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The config codec for {@link FancyText} (and its {@link FancyFormatter} subclass), registered centrally into
 * {@link ConfigFactory} by {@link #register()} (the {@code CFPositionFamily} style), so {@link FancyText} stays
 * a plain annotation-free POJO. It replicates the legacy save/load exactly: a bespoke, context-dependent
 * on-disk shape (a scalar string, a string-list, an object with text/hover/click, or a numbered formatter
 * object), with a tolerant read of every one of those shapes so old files keep reading.
 */
public final class FancyTextConfigCodec {

    private FancyTextConfigCodec() {
    }

    private static final String FANCY_KEY_FORMATTER = "formatter";
    private static final String FANCY_KEY_TEXT = "text";
    private static final String FANCY_KEY_HOVER = "hoverText";
    private static final String FANCY_KEY_ACTION_TEXT = "clickActionText";
    private static final String FANCY_KEY_ACTION_TYPE = "clickActionType";

    /**
     * Register {@link FancyText} into {@link ConfigFactory}. A base-class serializer applies to the
     * {@link FancyFormatter} subclass too, and the deserializer inspects the node so a formatter node still
     * reads back as a {@code FancyFormatter} through {@code getValue(path, FancyText.class)}.
     */
    public static void register() {
        ConfigFactory.register(FancyText.class).jackson(
                new JsonSerializer<FancyText>() {
                    @Override
                    public void serialize(FancyText value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        writeFancyText(value, gen);
                    }
                },
                new StdDeserializer<FancyText>(FancyText.class) {
                    @Override
                    public FancyText deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return readFancyText(parser.readValueAsTree());
                    }
                }
        );
    }

    private static void writeFancyText(FancyText fancyText, JsonGenerator gen) throws IOException {
        if (fancyText instanceof FancyFormatter) {
            FancyFormatter formatter = (FancyFormatter) fancyText;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(FANCY_KEY_FORMATTER, true);
            List<FancyText> children = formatter.getFancyTextList();
            for (int index = 0; index < children.size(); index++) {
                // The value is a FancyText; the mapper recurses back into this serializer for each child.
                map.put(String.valueOf(index + 1), children.get(index));
            }
            gen.writeObject(map);
            return;
        }

        boolean hasHover = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty();
        boolean hasAction = fancyText.getClickActionText() != null && !fancyText.getClickActionText().isEmpty();

        String text = fancyText.getText().replace('§', '&');
        Object saveText = asStringOrList(text);

        if (!hasHover && !hasAction) {
            gen.writeObject(saveText);
            return;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put(FANCY_KEY_TEXT, saveText);
        if (hasHover) {
            map.put(FANCY_KEY_HOVER, asStringOrList(fancyText.getHoverText().replace('§', '&')));
        }
        if (hasAction) {
            map.put(FANCY_KEY_ACTION_TEXT, asStringOrList(fancyText.getClickActionText().replace('§', '&')));
            map.put(FANCY_KEY_ACTION_TYPE, fancyText.getClickActionType().name());
        }
        gen.writeObject(map);
    }

    /** A multi-line string is stored as a string-list (split on newlines); a single line stays a scalar. */
    private static Object asStringOrList(String text) {
        return text.contains("\n") ? Arrays.asList(text.split("\n", -1)) : text;
    }

    private static FancyText readFancyText(JsonNode node) {
        if (node.isObject() && node.has(FANCY_KEY_FORMATTER)) {
            FancyFormatter formatter = FancyFormatter.of();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                if (key.equals(FANCY_KEY_FORMATTER)) {
                    continue;
                }
                formatter.append(readFancyText(node.get(key)));
            }
            return formatter;
        }

        if (node.isObject() && node.has(FANCY_KEY_TEXT)) {
            String text = joinNode(node.get(FANCY_KEY_TEXT));
            String hoverText = joinNode(node.get(FANCY_KEY_HOVER));
            String actionText = joinNode(node.get(FANCY_KEY_ACTION_TEXT));
            String actionTypeName = joinNode(node.get(FANCY_KEY_ACTION_TYPE));
            ClickActionType actionType = actionTypeName != null && !actionTypeName.isEmpty()
                    ? ClickActionType.valueOf(actionTypeName)
                    : ClickActionType.NONE;
            return new FancyText(
                    FCColorUtil.colorfy(text),
                    FCColorUtil.colorfy(hoverText),
                    FCColorUtil.colorfy(actionText),
                    actionType
            );
        }

        // Scalar or string-list: a plain text FancyText.
        return new FancyText(FCColorUtil.colorfy(joinNode(node)));
    }

    /** Collapse a node into a single string: a string-list joins on newlines, a scalar stays as-is, an
     *  absent/null node yields null (mirrors the legacy "String or List&lt;String&gt;" read). */
    private static String joinNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonNode element : node) {
                lines.add(element.asText());
            }
            return String.join("\n", lines);
        }
        return node.asText();
    }
}
