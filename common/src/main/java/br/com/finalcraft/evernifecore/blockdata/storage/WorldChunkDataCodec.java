package br.com.finalcraft.evernifecore.blockdata.storage;

import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.CodecException;
import br.com.finalcraft.everydatabase.codec.ObjectMapperAware;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The entity codec of one manager: the backend's own storage codec, with the {@code block -> value} map
 * re-bound to the concrete value type {@code O}.
 *
 * <p>Bytes are written through the {@link ObjectMapper} of the backend's default codec, so an entity lands
 * in the configured format (YAML or indented JSON on a file backend, compact JSON everywhere else) and the
 * values carry the platform types and {@code Ref} binding that mapper was built with. Decode cannot delegate
 * to that codec: {@link WorldChunkData} erases {@code O}, so Jackson would hand back raw
 * {@code LinkedHashMap} values. The envelope fields are read off the tree and the values map is converted
 * through an explicit {@code MapType(String, O)} - the type the erased field cannot supply.
 *
 * <p>Decoding the values through that tree means {@code ConfigLifecycle} hooks of {@code O} do NOT fire
 * here; a value type that needs work on load has to do it in its own Jackson (de)serializer.
 *
 * @param <O> the block-value type of the owning manager
 */
public final class WorldChunkDataCodec<O> implements Codec<WorldChunkData<O>>, ObjectMapperAware {

    private final ObjectMapper byteEdge;
    private final String contentType;
    private final MapType valuesType;

    public WorldChunkDataCodec(Class<O> valueType, ObjectMapper byteEdge, String contentType) {
        this.byteEdge = byteEdge;
        this.contentType = contentType;
        this.valuesType = byteEdge.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, valueType);
    }

    /**
     * Composes onto {@code base}, the default codec of the backend the collection lives on
     * ({@code ECStorage.defaultCodec(WorldChunkData.class)}): its mapper and content type are adopted, its
     * encode/decode is not.
     */
    public static <O> WorldChunkDataCodec<O> composing(Class<O> valueType, Codec<WorldChunkData> base) {
        if (!(base instanceof ObjectMapperAware)) {
            throw new CodecException("The default codec of this backend for WorldChunkData is a "
                    + base.getClass().getName() + ", which exposes no Jackson ObjectMapper, so the block"
                    + " values cannot be bound to their concrete type. Open the collection on a backend"
                    + " whose codec is ObjectMapperAware (every ECStorage backend is), or hand the mapper"
                    + " to the WorldChunkDataCodec constructor yourself.");
        }
        return new WorldChunkDataCodec<>(valueType, ((ObjectMapperAware) base).objectMapper(),
                base.contentType());
    }

    /**
     * The codec for a manager built on a raw {@code Storage} - TEST-ONLY, since production opens through an
     * {@code ECStorage}, which knows the format the admin configured. Compact JSON with the
     * {@code ConfigFactory} platform types and no {@code Ref} registry.
     */
    public static <O> WorldChunkDataCodec<O> jsonFallback(Class<O> valueType) {
        return composing(valueType, ConfigFactoryCodec.json(WorldChunkData.class, null));
    }

    @Override
    public byte[] encode(WorldChunkData<O> value) throws CodecException {
        try {
            return byteEdge.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new CodecException("Failed to encode the chunk '" + value.getChunkKey() + "'", e);
        }
    }

    @Override
    public WorldChunkData<O> decode(byte[] data) throws CodecException {
        try {
            JsonNode root = byteEdge.readTree(data);
            WorldChunkData<O> chunk = new WorldChunkData<>();
            JsonNode keyNode = root.get("chunkKey");
            if (keyNode != null && !keyNode.isNull()) {
                chunk.setChunkKey(keyNode.asText());
            }
            JsonNode gridNode = root.get("gridChunkSize");
            if (gridNode != null && !gridNode.isNull()) {
                chunk.setGridChunkSize(gridNode.asInt());
            }
            JsonNode valuesNode = root.get("values");
            if (valuesNode != null && !valuesNode.isNull()) {
                Map<String, O> values = byteEdge.convertValue(valuesNode, valuesType);
                chunk.setValues(values);
            }
            return chunk;
        } catch (Exception e) {
            throw new CodecException("Failed to decode a WorldChunkData", e);
        }
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public ObjectMapper objectMapper() {
        return byteEdge;
    }
}
