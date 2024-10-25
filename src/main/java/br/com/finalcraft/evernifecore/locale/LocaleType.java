package br.com.finalcraft.evernifecore.locale;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocaleType {
    public static final String EN_US = createLocale("EN_US");
    public static final String PT_BR = createLocale("PT_BR");

    private static Map<String,String> NORMALIZED_LOCALES = new LinkedHashMap<>();

    public static Collection<String> values() {
        return NORMALIZED_LOCALES.values();
    }

    public static String normalize(String name){
        return NORMALIZED_LOCALES.getOrDefault(name.toUpperCase(), name);
    }

    public static String createLocale(String name) {
        NORMALIZED_LOCALES.put(name, name);
        return name;
    }

}
