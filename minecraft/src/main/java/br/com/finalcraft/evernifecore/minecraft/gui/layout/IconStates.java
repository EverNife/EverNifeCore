package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.EverNifeCore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How an enum constant and a {@code States:} key in the yml name the same appearance.
 *
 * <p>{@code DEFAULT} is the icon's own look and therefore has no key at all; every other constant is
 * UPPER_SNAKE read as camelCase, so {@code SEM_ESTOQUE} is written {@code semEstoque} in the file.
 * The conversion is what lets an enum the plugin already has double as the state vocabulary without
 * the yml ever mentioning Java.</p>
 */
public final class IconStates {

    /** The constant that means "no state": the icon draws its own appearance. */
    public static final String DEFAULT = "DEFAULT";

    /** Reported keys, so a file nobody fixed does not repeat itself on every screen that opens. */
    private static final Set<String> WARNED =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private IconStates() {

    }

    /** The yml key of {@code constant}, or {@code ""} when it is the default appearance. */
    @Nonnull
    public static String keyOf(@Nonnull Enum<?> constant) {
        return keyOf(constant.name());
    }

    /** The yml key a constant named {@code constantName} maps to. */
    @Nonnull
    public static String keyOf(@Nonnull String constantName) {
        if (DEFAULT.equals(constantName)) {
            return "";
        }
        StringBuilder key = new StringBuilder(constantName.length());
        boolean upperNext = false;
        for (int index = 0; index < constantName.length(); index++) {
            char character = constantName.charAt(index);
            if (character == '_') {
                upperNext = key.length() > 0;
                continue;
            }
            key.append(upperNext ? Character.toUpperCase(character) : Character.toLowerCase(character));
            upperNext = false;
        }
        return key.toString();
    }

    /** The constant {@code key} names, or {@code null} when no constant of {@code type} answers to it. */
    @Nullable
    public static <E extends Enum<E>> E constantOf(@Nonnull Class<E> type, @Nullable String key) {
        String wanted = key == null ? "" : key.trim();
        for (E constant : type.getEnumConstants()) {
            if (keyOf(constant).equals(wanted)) {
                return constant;
            }
        }
        return null;
    }

    /** Every key {@code type} declares, in declaration order, the default one included as {@code ""}. */
    @Nonnull
    public static Set<String> keysOf(@Nonnull Class<? extends Enum<?>> type) {
        Set<String> keys = new LinkedHashSet<>();
        for (Enum<?> constant : type.getEnumConstants()) {
            keys.add(keyOf(constant));
        }
        return keys;
    }

    /**
     * Reports each declared key no constant of {@code type} answers to - an admin's typo under
     * {@code States:} that would otherwise be a state nothing ever selects.
     *
     * <p>A constant with no key of its own is the opposite case and is silent on purpose: a constant
     * nobody declared an appearance for draws the icon's own, which is how a domain enum is allowed to
     * carry more constants than the screen has looks.</p>
     */
    public static void warnUnknownKeys(@Nonnull Class<? extends Enum<?>> type,
                                       @Nonnull Collection<String> declaredKeys, @Nonnull String where) {
        Set<String> known = keysOf(type);
        Set<String> named = new LinkedHashSet<>(known);
        named.remove("");
        for (String declared : declaredKeys) {
            //the same enum with the same wrong key on two screens is two files to fix, so two warnings
            if (known.contains(declared) || !WARNED.add(type.getName() + '#' + declared + '#' + where)) {
                continue;
            }
            EverNifeCore.getLog().warning("The state '" + declared + "' of " + where + " matches no constant of "
                    + type.getSimpleName() + ", whose states are " + named + ", so nothing can ever select it. "
                    + "Fix the key in the yml, or add the constant to the enum.");
        }
    }

}
