package br.com.finalcraft.evernifecore.config.yaml.exeption;

public class ConfigLoadExeption extends RuntimeException{

    public ConfigLoadExeption(String message, Throwable cause) {
        super(message, cause);
    }

}
