package br.com.finalcraft.evernifecore.config.factory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reading the coordinates of a position written by hand as a map ({@code spawn: {worldName: world, x: 10, y: 64, z: 3}}),
 * for every position decoder of the core and the platforms.
 */
public final class PositionNodes {

    private PositionNodes() {
    }

    /**
     * The number under {@code key}; a quoted number is read too.
     *
     * @throws IllegalArgumentException naming the key and what the entry holds, when there is no number there
     */
    public static double requireNumber(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value != null && value.isNumber()) {
            return value.asDouble();
        }
        if (value != null && value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().trim());
            } catch (NumberFormatException notANumber) {
                //falls through to the refusal, which names the key
            }
        }
        throw new IllegalArgumentException("This position has no number under '" + key + "' (it reads " + node + ")."
                + " Write every coordinate, or remove the whole entry to leave it unset.");
    }

    /** The number under {@code key}, or {@code absent} when the key is not written; a key written with no number is refused. */
    public static double numberOr(JsonNode node, String key, double absent) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? absent : requireNumber(node, key);
    }
}
