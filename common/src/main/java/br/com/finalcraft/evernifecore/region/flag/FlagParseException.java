package br.com.finalcraft.evernifecore.region.flag;

/** A flag value that could not be read; the message names what was received and the accepted format. */
public class FlagParseException extends Exception {

    public FlagParseException(String message) {
        super(message);
    }

    public FlagParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
