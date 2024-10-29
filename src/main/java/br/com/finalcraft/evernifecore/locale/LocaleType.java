package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.util.FCReflectionUtil;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocaleType {
    public static final String EN_US = "EN_US";
    public static final String PT_BR = "PT_BR";

    private static Map<String,String> NORMALIZED_LOCALES = new LinkedHashMap<>();
    static {
        FCReflectionUtil.getDeclaredFields(LocaleType.class).stream()
                .filter(fieldAccessor -> fieldAccessor.getTheField().getType() == String.class)
                .map(fieldAccessor -> fieldAccessor.get(null))
                .forEach(locale -> NORMALIZED_LOCALES.put(locale.toString(), locale.toString()));
    }

    public static Collection<String> values() {
        return NORMALIZED_LOCALES.values();
    }

    public static String normalize(String name){
        return NORMALIZED_LOCALES.getOrDefault(name.toUpperCase(), name);
    }

    public static Map<String, String> getNormalizedLocalesMap() {
        return NORMALIZED_LOCALES;
    }
}
