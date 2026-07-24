package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;

import java.util.List;
import java.util.function.Function;

public interface ILocaleMessageBase {

    void send(FCommandSender... commandSenders);

    default void send(List<FCommandSender> commandSenders) {
        send(commandSenders.toArray(new FCommandSender[0]));
    }

    void broadcast();

    SendCustom addReplacer(CompoundReplacer compoundReplacer);

    SendCustom addPlaceholder(String placeHolder, Object value);

    SendCustom addPlaceholder(String placeHolder, Function<PlayerData, Object> function);

    SendCustom addHover(String hover);

    SendCustom addAction(String action);

    SendCustom addSuggest(String suggest);

    SendCustom addLink(String link);

    SendCustom concat(LocaleMessage localeMessage);

    SendCustom concat(SendCustom sendCustom);

    FancyText getFancyText(FCommandSender sender);

}
