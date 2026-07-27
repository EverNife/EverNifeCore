package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;

public class FancyTextManager {

    public static void send(FancyText fancyText, FCommandSender... commandSenders) {
        // Rendered per recipient because a placeholder may resolve differently for each of them.
        // One function, so a formatter travelling as a FancyText can never take a different route.
        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(fancyText.toComponent(RenderContext.of(sender)));
        }
    }

    /**
     * Same delivery, but with the command context taken from {@code context} instead of from the
     * scope open on the sending thread - which is what a message built inside a command and
     * delivered from a task later needs. Each recipient still gets their own render.
     */
    public static void send(FancyText fancyText, RenderContext context, FCommandSender... commandSenders) {
        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(fancyText.toComponent(context.forRecipient(sender)));
        }
    }
}
