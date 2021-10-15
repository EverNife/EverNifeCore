package br.com.finalcraft.evernifecore.locale;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum LocaleType {
    EN_US,
    PT_BR;

    public static List<String> getAllNames(){
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toList());
    }
}
