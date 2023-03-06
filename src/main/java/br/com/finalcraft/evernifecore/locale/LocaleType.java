package br.com.finalcraft.evernifecore.locale;

public class LocaleType {
    public static final String EN_US = "EN_US";
    public static final String PT_BR = "PT_BR";

    public static String[] values() {
        return new String[] { LocaleType.EN_US, LocaleType.PT_BR };
    }

    public static String normalize(String name){
        return EN_US.equalsIgnoreCase(name)  ? EN_US
             : PT_BR.equalsIgnoreCase(name)  ? PT_BR
             : name;
    }

}
