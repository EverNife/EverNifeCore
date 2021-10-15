package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SendCustomComplex extends SendCustom {

    private List<SendCustom> concatList = new ArrayList<>();

    protected SendCustomComplex(LocaleMessage localeMessage, SendCustom previous) {
        super(localeMessage);
        if (previous instanceof SendCustomComplex){
            concatList = ((SendCustomComplex)previous).concatList;
        }else {
            concatList.add(previous);
        }
        concatList.add(this);
    }

    public SendCustomComplex(SendCustom sendCustom, SendCustom previous) {
        super(sendCustom.localeMessage);
        this.mapOfPlaceholders = sendCustom.mapOfPlaceholders;
        this.hover = sendCustom.hover;
        this.action = sendCustom.action;
        this.suggest = sendCustom.suggest;

        if (previous instanceof SendCustomComplex){
            concatList = ((SendCustomComplex)previous).concatList;
        }else {
            concatList.add(previous);
        }
        concatList.add(this);
    }

    @Override
    public void send(CommandSender... commandSenders){
        for (CommandSender sender : commandSenders) {

            List<FancyText> fancyTexts = new ArrayList<>();
            for (SendCustom sendCustom : concatList) {

                FancyText fancyText = sendCustom.localeMessage.getFancyText(sender).clone();
                if (sendCustom.hover != null) fancyText.setHoverText(sendCustom.hover);
                if (sendCustom.action != null) fancyText.setRunCommandAction(sendCustom.action);
                if (sendCustom.suggest != null) fancyText.setSuggestCommandAction(sendCustom.suggest);

                LocaleMessageImp localeMessageImp = (LocaleMessageImp) sendCustom.localeMessage;
                List<Map.Entry<String, Object>> allPlaceholdersReplacers = new ArrayList<>();
                allPlaceholdersReplacers.addAll(sendCustom.mapOfPlaceholders.entrySet()); //Custom placeholders, created by demand
                allPlaceholdersReplacers.addAll(localeMessageImp.getContextPlaceholders().entrySet()); //Context Placeholders, like %label%

                final PlayerData playerData = PlayerController.getPlayerData((Player) sender);
                for (Map.Entry<String, Object> entry : allPlaceholdersReplacers) {
                    String placeholder = entry.getKey();
                    String value;
                    if (entry.getValue() instanceof Function){
                        if (sender instanceof Player == false){
                            continue; //Only evaluate Player placeholders in this case
                        }
                        value = String.valueOf(((Function<PlayerData, Object>)entry.getValue()).apply(playerData));
                    }else {
                        value = String.valueOf(entry.getValue());
                    }

                    fancyText.replace(placeholder, value);
                }

                fancyTexts.add(fancyText);
            }

            FancyText.sendTo(sender, fancyTexts);
        }
    }
}