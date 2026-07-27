package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class SendCustom implements ILocaleMessageBase {

    protected final LocaleMessage localeMessage;
    protected CompoundReplacer compoundReplacer = new CompoundReplacer();
    protected Map<String, Object> declaredPlaceholders = new LinkedHashMap<>();

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
        declaredPlaceholders.put(placeHolder, value);
        return this;
    }

    @Override
    public SendCustom addPlaceholder(String placeHolder, Function<PlayerData, Object> function) {
        declaredPlaceholders.put(placeHolder, function);
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
        FCommandSender[] onlinePlayers = EverNifeCore.getProviders().getPlatform()
                .getOnlinePlayers()
                .toArray(new FCommandSender[0]);
        send(onlinePlayers);
    }

    @Override
    public FancyText getFancyText(@Nullable FCommandSender sender){
        return renderFor(sender);
    }

    /**
     * Renders this single piece - its locale text plus the decorations and placeholders declared on
     * it - for one recipient. Whatever {@link #send(FCommandSender...)} delivers is built from here,
     * so a preview can never describe something else.
     */
    protected FancyText renderFor(@Nullable FCommandSender sender){
        FancyText fancyText = sender == null ? localeMessage.getDefaultFancyText().copy() : localeMessage.getFancyText(sender).copy();
        if (hover != null) fancyText.hover(hover);
        if (action != null) fancyText.clickCommand(action);
        if (suggest != null) fancyText.clickSuggest(suggest);
        if (link != null) fancyText.clickLink(link);

        // Declared, not resolved: the recipient is only known at render time, and ${label} and its
        // friends answer for themselves wherever this text ends up being rendered.
        fancyText.addPlaceholders(declaredPlaceholders);
        fancyText.addReplacer(compoundReplacer);

        return fancyText;
    }


}
