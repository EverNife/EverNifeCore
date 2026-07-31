package br.com.finalcraft.evernifecore.fancytext;

/**
 * The keys any message can cite, whatever built it - a locale message, a hand-built piece or a chain
 * assembled at runtime. Lowest precedence of all: a message that declares its own {@code label}
 * shadows this one, because its own declaration is applied first.
 */
public final class CoreFancyMessageParsers {

    public static final MessagePlaceholders INSTANCE = new MessagePlaceholders();

    static {
        INSTANCE.declare("label", "The command label the player typed",
                context -> context.getMessageContext().getLabel());
        INSTANCE.declare("subcmd", "The sub-command name being executed",
                context -> context.getMessageContext().getSubCommandName());
        INSTANCE.declare("path", "Every token below the label, as typed",
                context -> context.getMessageContext().getPathText());
        INSTANCE.declare("parentpath", "The path minus what the deepest sub-command owns",
                context -> context.getMessageContext().getParentPathText());
    }

    private CoreFancyMessageParsers() {
    }
}
