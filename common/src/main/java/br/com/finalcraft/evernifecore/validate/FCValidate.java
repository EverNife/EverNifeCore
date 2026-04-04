package br.com.finalcraft.evernifecore.validate;

public class FCValidate {

    public static <T> T notNull(T object, String message, Object... values) {
        if (object == null) {
            throw new NullPointerException(String.format(message, values));
        }
        return object;
    }

}
