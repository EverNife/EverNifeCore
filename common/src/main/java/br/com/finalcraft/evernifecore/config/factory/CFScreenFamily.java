package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.screen.SlotSet;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The config codecs of the screen package. */
final class CFScreenFamily {

    private CFScreenFamily() {

    }

    static void register() {
        registerSlotSet();
    }

    /**
     * The single slot-list codec. On disk a slot list is written three ways - the bracketed string {@code "[1,2,3]"}, a
     * YAML list, and a bare number for one slot - and all three are read here; only the bracketed string is written.
     *
     * <p>An empty list means "nowhere", which is how an admin switches an icon off, and is never an error. Text that is
     * none of the three IS an error, thrown naming the value: only the caller knows which field of which file holds it.</p>
     */
    private static void registerSlotSet() {
        ConfigFactory.register(SlotSet.class).jackson(
                new JsonSerializer<SlotSet>() {
                    @Override
                    public void serialize(SlotSet value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                        gen.writeString(value.serialize());
                    }
                },
                new StdDeserializer<SlotSet>(SlotSet.class) {
                    @Override
                    public SlotSet deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                        return readSlotSet(parser.readValueAsTree());
                    }
                }
        );
    }

    private static SlotSet readSlotSet(JsonNode node) {
        if (node == null || node.isNull()) {
            return SlotSet.EMPTY;
        }
        if (node.isArray()) {
            List<Integer> slots = new ArrayList<>();
            for (JsonNode element : node) {
                for (int slot : SlotSet.parse(element.asText()).toArray()) {
                    slots.add(slot);
                }
            }
            int[] values = new int[slots.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = slots.get(i);
            }
            return SlotSet.of(values);
        }
        if (node.isNumber()) {
            return SlotSet.of(node.asInt());
        }
        return SlotSet.parse(node.asText());
    }

}
