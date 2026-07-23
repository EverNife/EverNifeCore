package br.com.finalcraft.evernifecore.argumento;

/**
 * A single {@code --name value} flag extracted by {@link MultiArgumentos#flagify()}.
 */
public class FlagedArgumento extends Argumento {

    public final static FlagedArgumento EMPTY_ARG = new FlagedArgumento();
    private final String flagName;

    private FlagedArgumento() {
        super("false");
        flagName = "";
    }

    /**
     * @param flagName the flag's name with its leading dashes already stripped by the tokenizer
     * @param value the flag's value, already unquoted by the tokenizer ("true" for a presence flag)
     */
    public FlagedArgumento(String flagName, String value) {
        super(value);
        this.flagName = flagName;
    }

    public boolean isSet(){
        return !flagName.isEmpty();
    }

    public String getFlagName() {
        return flagName;
    }

    public String getFlagValue() {
        return super.toString();
    }

    @Override
    public String toString() {
        return "--" + flagName + " " + super.toString();
    }
}
