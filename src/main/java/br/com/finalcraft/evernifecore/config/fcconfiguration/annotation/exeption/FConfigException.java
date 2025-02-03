package br.com.finalcraft.evernifecore.config.fcconfiguration.annotation.exeption;

public class FConfigException extends RuntimeException{

    public FConfigException(String message) {
        super(message);
    }

    public FConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public FConfigException(Throwable cause) {
        super(cause);
    }

    public FConfigException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
