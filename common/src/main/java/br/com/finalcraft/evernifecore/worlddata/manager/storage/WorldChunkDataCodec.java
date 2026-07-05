package br.com.finalcraft.evernifecore.worlddata.manager.storage;

import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.CodecException;
import br.com.finalcraft.everydatabase.codec.JacksonConfig;
import br.com.finalcraft.everydatabase.codec.ObjectMapperAware;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.MapType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link Codec} for {@link WorldChunkData} that restores the {@code block -> value} map to the
 * concrete value type {@code O} of a given manager. The entity class erases {@code O} (its field is
 * {@code Map<String, Object>}), so a plain {@code readValue(bytes, WorldChunkData.class)} would leave
 * each value as a raw {@code LinkedHashMap}; this codec re-binds the {@code values} map through an
 * explicit {@code MapType(String, O)}, giving Jackson the concrete type it cannot infer from the
 * erased field.
 *
 * @param <O> the concrete block-value type handled by the owning manager
 */
public final class WorldChunkDataCodec<O> implements Codec<WorldChunkData>, ObjectMapperAware {

    private final ObjectMapper mapper;
    private final MapType valuesType;

    public WorldChunkDataCodec(Class<O> valueType) {
        this(valueType, JacksonConfig.storageSafe(new JsonMapper()));
    }

    public WorldChunkDataCodec(Class<O> valueType, ObjectMapper mapper) {
        this.mapper = mapper;
        this.valuesType = mapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, valueType);
    }

    /** Pretty-printing variant for human-readable per-entity files on file backends. */
    public static <O> WorldChunkDataCodec<O> pretty(Class<O> valueType) {
        return new WorldChunkDataCodec<>(valueType,
                JacksonConfig.storageSafe(new JsonMapper()).enable(SerializationFeature.INDENT_OUTPUT));
    }

    @Override
    public byte[] encode(WorldChunkData value) throws CodecException {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new CodecException("Failed to encode WorldChunkData to JSON", e);
        }
    }

    @Override
    public WorldChunkData decode(byte[] data) throws CodecException {
        try {
            JsonNode root = mapper.readTree(data);
            WorldChunkData chunk = new WorldChunkData();
            JsonNode keyNode = root.get("chunkKey");
            if (keyNode != null && !keyNode.isNull()) {
                chunk.setChunkKey(keyNode.asText());
            }
            JsonNode valuesNode = root.get("values");
            if (valuesNode != null && !valuesNode.isNull()) {
                Map<String, Object> values = mapper.convertValue(valuesNode, valuesType);
                chunk.setValues(values);
            }
            return chunk;
        } catch (Exception e) {
            throw new CodecException("Failed to decode WorldChunkData from JSON", e);
        }
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public ObjectMapper objectMapper() {
        return mapper;
    }
}
