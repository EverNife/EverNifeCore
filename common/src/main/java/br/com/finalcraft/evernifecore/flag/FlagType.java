package br.com.finalcraft.evernifecore.flag;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * How a flag's value is read from a command, shown back, stored and completed. {@link FlagTypes} has the stock
 * ones; a plugin may implement its own.
 */
public interface FlagType<T> {

    /** {@code tristate}, {@code text}, {@code integer}, {@code double}, {@code enum}, {@code location}, or the plugin's own id. */
    String getId();

    Class<T> getValueType();

    /** Reads what a player typed; a composite value takes {@code k=v k2=v2}. */
    T parse(String text) throws FlagParseException;

    /** The inverse of {@link #parse}: {@code parse(format(v))} gives {@code v} back. */
    String format(T value);

    JsonNode toJson(T value);

    T fromJson(JsonNode node) throws FlagParseException;

    List<String> tabComplete(String partial);
}
