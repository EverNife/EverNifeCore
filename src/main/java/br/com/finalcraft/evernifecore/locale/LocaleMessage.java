package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.command.CommandSender;

import java.util.function.Function;

public interface LocaleMessage {

    public void send(CommandSender... commandSenders);

    @Deprecated
    public SendCustom custom();

    public SendCustom addPlaceholder(String placeHolder, Object value);

    public SendCustom addPlaceholder(String placeHolder, Function<PlayerData, Object> function);

    public SendCustom addHover(String hover);

    public SendCustom addAction(String action);

    public SendCustom addSuggest(String suggest);

    public SendCustom concat(LocaleMessage localeMessage);

    public SendCustom concat(SendCustom sendCustom);

    public FancyText getFancyText(CommandSender sender);

}
