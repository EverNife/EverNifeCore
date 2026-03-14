package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import com.hypixel.hytale.server.core.universe.Universe;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SendCustom implements ILocaleMessageBase {

    protected final LocaleMessage localeMessage;
    protected CompoundReplacer compoundReplacer = new CompoundReplacer();
    protected Map<String, Object> mapOfPlaceholders = new HashMap<String, Object>();

    protected transient String hover;
    protected transient String action;
    protected transient String suggest;
    protected transient String link;

    protected SendCustom(LocaleMessage localeMessage) {
        this.localeMessage = localeMessage;
    }

    @Override
    public SendCustom addReplacer(CompoundReplacer compoundReplacer) {
        this.compoundReplacer.appendReplacer(compoundReplacer);
        return this;
    }

    @Override
    public SendCustom addPlaceholder(String placeHolder, Object value) {
        mapOfPlaceholders.put(placeHolder, value);
        return this;
    }

    @Override
    public SendCustom addPlaceholder(String placeHolder, Function<PlayerData, Object> function) {
        mapOfPlaceholders.put(placeHolder, function);
        return this;
    }

    @Override
    public SendCustom addHover(String hover) {
        this.hover = hover;
        return this;
    }

    @Override
    public SendCustom addAction(String action) {
        this.action = action;
        return this;
    }

    @Override
    public SendCustom addSuggest(String suggest) {
        this.suggest = suggest;
        return this;
    }

    @Override
    public SendCustom addLink(String link) {
        this.link = link;
        return this;
    }

    @Override
    public SendCustom concat(LocaleMessage localeMessage) {
        return new SendCustomComplex(localeMessage, this);
    }

    @Override
    public SendCustom concat(SendCustom sendCustom) {
        return new SendCustomComplex(sendCustom, this);
    }

    @Override
    public void send(FCommandSender... commandSenders) {
        for (FCommandSender sender : commandSenders) {
            FancyText fancyText = getFancyText(sender);
            fancyText.send(sender);
        }
    }

    @Override
    public void broadcast(){
        send(Universe.get().getPlayers().toArray(new FCommandSender[0]));
    }

    @Override
    public FancyText getFancyText(@Nullable FCommandSender sender){
        FancyText fancyText = sender == null ? localeMessage.getDefaultFancyText().clone() : localeMessage.getFancyText(sender).clone();
        if (hover != null) fancyText.setHoverText(hover);
        if (action != null) fancyText.setRunCommandAction(action);
        if (suggest != null) fancyText.setSuggestCommandAction(suggest);
        if (link != null) fancyText.setOpenLinkAction(link);

        LocaleMessageImp localeMessageImp = (LocaleMessageImp) localeMessage;
        List<Map.Entry<String, Object>> allPlaceholdersReplacers = new ArrayList<Map.Entry<String, Object>>();
        allPlaceholdersReplacers.addAll(mapOfPlaceholders.entrySet()); //Custom placeholders, created by demand
        allPlaceholdersReplacers.addAll(localeMessageImp.getContextPlaceholders().entrySet()); //Context Placeholders, like %label%

        boolean isPlayer = sender instanceof FPlayer;
        final PlayerData playerData = isPlayer ? PlayerController.getPlayerData(sender.getUniqueId()) : null;
        for (Map.Entry<String, Object> entry : allPlaceholdersReplacers) {
            String placeholder = entry.getKey();
            String value;
            if (isPlayer && entry.getValue() instanceof Function) {
                value = String.valueOf(((Function<PlayerData, Object>) entry.getValue()).apply(playerData));
            } else {
                value = String.valueOf(entry.getValue());
            }

            fancyText.replace(placeholder, value);
        }

        fancyText.replace(compoundReplacer);

        return fancyText;
    }


}