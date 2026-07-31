package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything one invocation has resolved so far, in a single bag: a token, a flag, an ancestor's
 * capture and a parameter read off the invocation all land here, so a parser asking what came before
 * asks one question instead of guessing which family produced the answer.
 * <p>
 * Two ways in and out. By TYPE answers with what was filed under that exact class and only then with
 * the most recently resolved value that fits, which is a convenience and admits ties; by declared NAME
 * answers exactly, which is why a name is refused at registration when a method declares it twice.
 * Prefer the name when it matters which one you get.
 * <p>
 * Writing is the framework's: a parser only ever reaches this through the lookups its call exposes.
 */
public final class ResolvedArguments {

    private final Map<Class<?>, Object> byType = new LinkedHashMap<>();
    private final Map<String, Object> byName = new LinkedHashMap<>();

    /** A bag nothing ever resolved into - what a parser exercised outside a dispatch reads. */
    public static ResolvedArguments none() {
        return new ResolvedArguments();
    }

    /**
     * Records what one parameter came out as. A null value is an absence, not an entry: "did not
     * resolve" and "resolved to nothing" are the same answer to every reader.
     *
     * @param declaredName the name as the annotation spells it ({@code "<player>"}, {@code "--force"}),
     *                     or empty for a parameter that declares none
     */
    public void resolved(@Nullable String declaredName, @Nullable Object value) {
        if (value == null){
            return;
        }
        //Re-put so insertion order IS recency order, which is what a type lookup promises
        byType.remove(value.getClass());
        byType.put(value.getClass(), value);

        if (declaredName != null && !declaredName.isEmpty()){
            byName.remove(declaredName);
            byName.put(declaredName, value);
        }
    }

    /**
     * The value filed under exactly this class or, when nothing was, the most recently resolved value
     * the class accepts. An exact match wins even when a subtype resolved later, so asking for a base
     * type never hides a value of that very type. Null when the invocation produced neither.
     */
    public <T> @Nullable T get(@Nonnull Class<T> type) {
        Object exact = byType.get(type);
        if (exact != null){
            return type.cast(exact);
        }

        //A value is filed under its concrete class, so asking for an interface or a base type has to
        //walk - newest last, so the last match found is the most recent one
        Object mostRecent = null;
        for (Object candidate : byType.values()) {
            if (type.isInstance(candidate)){
                mostRecent = candidate;
            }
        }
        return type.cast(mostRecent);
    }

    /**
     * The value of the parameter that declared exactly this name, or null - including when something
     * else of that name resolved to another type entirely.
     */
    public <T> @Nullable T get(@Nonnull String declaredName, @Nonnull Class<T> type) {
        Object value = byName.get(declaredName);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
