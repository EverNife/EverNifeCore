package br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception;

/**
 * Every refusal the framework makes while MOUNTING a command: a shape the declaration cannot have, a
 * parser that cannot be built for it, a context() its type cannot read. One type for all of them, so a
 * boot that wants to survive a broken command catches one thing and knows it is about the declaration
 * and never about what somebody typed.
 */
public class ArgMountException extends IllegalArgumentException{

    public ArgMountException(String message) {
        super(message);
    }

    /**
     * @param cause what refused underneath - kept so the stack still points at the line that decided,
     * even when the message above it is the one worth reading
     */
    public ArgMountException(String message, Throwable cause) {
        super(message, cause);
    }

}
