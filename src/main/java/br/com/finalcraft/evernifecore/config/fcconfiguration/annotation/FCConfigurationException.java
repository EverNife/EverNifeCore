package br.com.finalcraft.evernifecore.config.fcconfiguration.annotation;

public class FCConfigurationException extends RuntimeException{

    public FCConfigurationException(String message) {
        super(message);
    }

    public FCConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public FCConfigurationException(Throwable cause) {
        super(cause);
    }

    public FCConfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
