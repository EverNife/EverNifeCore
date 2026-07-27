package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;

public class FancyTextManager {

    public static void send(FancyText fancyText, FCommandSender... commandSenders) {
        // Rendered per recipient because a placeholder may resolve differently for each of them; a
        // text that cites none falls straight back to the shared, already cached component. One
        // function, so a formatter travelling as a FancyText can never take a different route.
        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(fancyText.toComponent(RenderContext.of(sender)));
        }
    }
}
