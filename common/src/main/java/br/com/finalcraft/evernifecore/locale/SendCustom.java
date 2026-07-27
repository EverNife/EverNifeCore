package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.MessageContext;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.fancytext.RenderContext;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A per-send decoration of a message: the hover, the click and the placeholder declarations that
 * apply to THIS send and to no other. The message it decorates is never touched - every render
 * starts from a fresh copy.
 */
public class SendCustom implements ILocaleMessageBase {

    protected final ChainPiece source;

    // Declarations are replayed onto the copy at render time, in the order they were made: the
    // recipient is only known then, and ${label} and its friends answer for themselves wherever
    // this text ends up being rendered.
    protected final List<Consumer<FancyText>> declarations = new ArrayList<>();

    protected transient String hover;
    protected transient String clickActionText;
    protected transient ClickActionType clickActionType;

    // Captured here, not at send time: a message built inside a command and delivered from a task
    // later still knows which label produced it, because the task's thread has no scope of its own.
    protected final MessageContext messageContext;

    protected SendCustom(LocaleMessage localeMessage) {
        this(ChainPiece.of(localeMessage));
    }

    protected SendCustom(ChainPiece source) {
        this.source = source;
        this.messageContext = MessageScope.currentOrEmpty();
    }

    @Override
    public SendCustom setHover(String hover) {
        this.hover = hover;
        return this;
    }

    @Override
    public SendCustom setClick(String clickActionText, ClickActionType actionType) {
        this.clickActionText = clickActionText;
        this.clickActionType = actionType;
        return this;
    }

    @Override
    public SendCustom addPlaceholder(String key, Object value) {
        declarations.add(fancyText -> fancyText.addPlaceholder(key, value));
        return this;
    }

    @Override
    public SendCustom addPlaceholder(String key, Supplier<?> value) {
        declarations.add(fancyText -> fancyText.addPlaceholder(key, value));
        return this;
    }

    @Override
    public SendCustom addPlaceholder(String key, Function<PlayerData, ?> value) {
        declarations.add(fancyText -> fancyText.addPlaceholder(key, value));
        return this;
    }

    @Override
    public SendCustom addPlaceholders(Map<String, ?> values) {
        declarations.add(fancyText -> fancyText.addPlaceholders(values));
        return this;
    }

    @Override
    public SendCustom addParser(String key, String description, Function<RenderContext, ?> parser) {
        declarations.add(fancyText -> fancyText.addParser(key, description, parser));
        return this;
    }

    @Override
    public SendCustom addReplacer(CompoundReplacer compoundReplacer) {
        declarations.add(fancyText -> fancyText.addReplacer(compoundReplacer));
        return this;
    }

    @Override
    public SendCustom append(LocaleMessage localeMessage) {
        return new SendCustomComplex(ChainPiece.of(localeMessage), this);
    }

    @Override
    public SendCustom append(SendCustom sendCustom) {
        SendCustomComplex appended = new SendCustomComplex(sendCustom.source, this);
        appended.adoptDecorationsOf(sendCustom);
        return appended;
    }

    @Override
    public SendCustom append(FancyText fancyText) {
        // Snapshotted on the way in, the same policy FancyFormatter.append follows: the caller may go
        // on mutating the value it appended without ever reshaping this chain.
        return new SendCustomComplex(ChainPiece.of(fancyText.copy()), this);
    }

    @Override
    public SendCustom append(String text) {
        return append(FancyText.of(text));
    }

    @Override
    public void send(FCommandSender... commandSenders) {
        send(RenderContext.of(null, messageContext), commandSenders);
    }

    @Override
    public void broadcast(){
        // The same audience FancyText.broadcast() reaches, console included - asking the chat adapter
        // instead of listing the online players is what keeps the two routes from diverging.
        send(EverNifeCore.getPlatform().getChatAdapter().getBroadcastAudience());
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
        FancyText fancyText = source.renderFor(sender);
        if (hover != null) fancyText.setHover(hover);
        if (clickActionType != null) fancyText.setClick(clickActionText, clickActionType);

        for (Consumer<FancyText> declaration : declarations) {
            declaration.accept(fancyText);
        }

        return fancyText;
    }

    /** Takes over another piece's decorations, for the append that continues decorating it. */
    void adoptDecorationsOf(SendCustom other) {
        this.declarations.addAll(other.declarations);
        this.hover = other.hover;
        this.clickActionText = other.clickActionText;
        this.clickActionType = other.clickActionType;
    }

}
