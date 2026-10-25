package br.com.finalcraft.evernifecore.logic;

/**
 * A value read for a context - a player's level, health, playtime, a placeholder, a plugin's own counter. Registered in
 * {@link Variables#registry()}, a variable is compared by {@code compare}, written in a text as {@code {id}} and, when
 * {@link MutableVariable mutable}, changed by {@code set}/{@code add}. Read on the caller's thread, without blocking I/O.
 */
public interface Variable<T> {

    /** The id a definition writes: the bare name of a stock variable, {@code owner:name} otherwise. */
    String getId();

    /** {@code BigDecimal}, {@code String}, {@code Boolean} or {@code Duration} - what {@code compare} reads it as. */
    Class<T> getType();

    /** The value for {@code ctx}; may throw when the context lacks what the variable reads (a player). */
    T read(ExecutionContext ctx);
}
