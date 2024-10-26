package br.com.finalcraft.evernifecore.locale;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocaleType {
    public static final String EN_US = "EN_US";
    public static final String PT_BR = "PT_BR";

    private static Map<String,String> NORMALIZED_LOCALES = new LinkedHashMap<>();
    static {
        NORMALIZED_LOCALES.put("en_us", EN_US);
        NORMALIZED_LOCALES.put("pt_br", PT_BR);
    }

    public static Collection<String> values() {
        return NORMALIZED_LOCALES.values();
    }

    public static String normalize(String name){
        return NORMALIZED_LOCALES.getOrDefault(name.toUpperCase(), name);
    }

}
