package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import net.kyori.adventure.text.Component;

import java.util.Map;

public class FancyTextManager {

    public static void send(FancyText fancyText, FCommandSender... commandSenders) {
        // Rendered per recipient because a placeholder may resolve differently for each of them; a
        // text that declares none falls straight back to the shared, already cached component.
        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(fancyText.toComponent(RenderContext.of(sender)));
        }
    }

    public static void send(FancyFormatter fancyFormatter, FCommandSender... commandSenders) {
        if (!fancyFormatter.hasPlaceholders()) {
            send((FancyText) fancyFormatter, commandSenders);
            return;
        }

        if (fancyFormatter.complexPlaceholder) {
            for (FCommandSender sender : commandSenders) {
                RenderContext context = RenderContext.of(sender);
                FancyFormatter formatterClone = fancyFormatter.copy();

                for (Map.Entry<String, Object> entry : formatterClone.mapOfPlaceholders.entrySet()) {
                    String value = context.resolveMappedValue(entry.getValue());
                    if (value == null) {
                        continue;   // per-player value with no PlayerData: the token stays as written
                    }
                    formatterClone.replace(entry.getKey(), value);
                }

                Component component = formatterClone.toComponent(context);
                sender.sendMessage(component);
            }
            return;
        }

        FancyFormatter formatterClone = fancyFormatter.copy();
        for (Map.Entry<String, Object> entry : fancyFormatter.mapOfPlaceholders.entrySet()) {
            String placeholder = entry.getKey();
            String value = String.valueOf(entry.getValue());
            formatterClone.replace(placeholder, value);
        }

        send((FancyText) formatterClone, commandSenders);
    }
}
