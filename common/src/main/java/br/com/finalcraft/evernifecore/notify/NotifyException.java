package br.com.finalcraft.evernifecore.notify;

/** A notify chain that cannot work here, or names a channel nobody registered; the message says how to fix it. */
public class NotifyException extends RuntimeException {

    public NotifyException(String message) {
        super(message);
    }
}
