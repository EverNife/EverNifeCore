package br.com.finalcraft.evernifecore.locale;

import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class LocaleType {
    public static final String EN_US = "EN_US";
    public static final String PT_BR = "PT_BR";
    public static final String ZH_CN = "ZH_CN";

    private static final Set<String> REGISTERED =
            new LinkedHashSet<>(Arrays.asList(EN_US, PT_BR, ZH_CN));

    /**
     * The canonical form of a locale name, known or not. Uppercasing with {@link Locale#ROOT} rather
     * than the JVM default keeps the key stable on a machine whose locale maps 'i' to something else
     * (Turkish being the classic case).
     */
    public static String normalize(@Nullable String name) {
        return name == null ? null : name.toUpperCase(Locale.ROOT);
    }

    /** Makes a custom locale visible to {@link #values()}, and so to the /eclocale listing. */
    public static String register(String name) {
        String normalized = normalize(name);
        REGISTERED.add(normalized);
        return normalized;
    }

    public static Collection<String> values() {
        return Collections.unmodifiableCollection(REGISTERED);
    }
}
