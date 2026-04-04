package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.function.Function;

public class FancyTextManager {

    public static void send(FancyText fancyText, FCommandSender... commandSenders) {
        if (fancyText.fancyFormatter != null) {
            send(fancyText.fancyFormatter, commandSenders);
            return;
        }

        Component component = fancyText.toComponent();

        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(component);
        }
    }

    public static void send(FancyFormatter fancyFormatter, FCommandSender... commandSenders) {
        if (!fancyFormatter.hasPlaceholders()) {
            Component component = fancyFormatter.toComponent();
            
            for (FCommandSender sender : commandSenders) {
                sender.sendMessage(component);
            }
            return;
        }

        if (fancyFormatter.complexPlaceholder) {
            for (FCommandSender sender : commandSenders) {
                FancyFormatter formatterClone = fancyFormatter.clone();
                final boolean isPlayer = sender instanceof FPlayer;
                final PlayerData playerData = isPlayer ? PlayerController.getPlayerData(sender.getUniqueId()) : null;
                
                for (Map.Entry<String, Object> entry : formatterClone.mapOfPlaceholders.entrySet()) {
                    String placeholder = entry.getKey();
                    String value;
                    if (isPlayer && entry.getValue() instanceof Function) {
                        value = String.valueOf(((Function<PlayerData, Object>) entry.getValue()).apply(playerData));
                    } else {
                        value = String.valueOf(entry.getValue());
                    }
                    formatterClone.replace(placeholder, value);
                }

                Component component = formatterClone.toComponent();
                sender.sendMessage(component);
            }
            return;
        }

        FancyFormatter formatterClone = fancyFormatter.clone();
        for (Map.Entry<String, Object> entry : fancyFormatter.mapOfPlaceholders.entrySet()) {
            String placeholder = entry.getKey();
            String value = String.valueOf(entry.getValue());
            formatterClone.replace(placeholder, value);
        }

        Component component = formatterClone.toComponent();
        
        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(component);
        }
    }
}
