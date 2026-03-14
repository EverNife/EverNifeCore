package br.com.finalcraft.evernifecore.config.yaml.exeption;

public class ConfigSaveExeption extends RuntimeException{

    public ConfigSaveExeption(String message, Throwable cause) {
        super(message, cause);
    }

}
