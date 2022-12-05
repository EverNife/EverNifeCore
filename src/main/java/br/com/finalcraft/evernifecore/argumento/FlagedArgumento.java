package br.com.finalcraft.evernifecore.argumento;

import java.util.regex.Pattern;

public class FlagedArgumento extends Argumento {

    public final static FlagedArgumento EMPTY_ARG = new FlagedArgumento();
    private final String flagName;

    private FlagedArgumento() {
        super("false");
        flagName = "";
    }

    public FlagedArgumento(String argumento) {
        super(argumento.contains(":") ? argumento.split(Pattern.quote(":"))[1] : "true");
        flagName = argumento.split(Pattern.quote(":"))[0].substring(0).toLowerCase();
    }

    public boolean isSet(){
        return !flagName.isEmpty();
    }

    public String getFlagName() {
        return flagName;
    }

    @Override
    public String toString() {
        return flagName + ':' + super.toString();
    }
}
