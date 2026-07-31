package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.RenderContext;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.SendCustom;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A message with a fixed text and no locale file behind it, for a test that needs an
 * {@link ILocaleMessageBase} it can identify and then find again in what a sender received. The text
 * goes out verbatim, so {@code TestCommandSender.assertAnyMessageContains} reads exactly what was
 * declared here.
 * <p>
 * Only what a message is asked for on the way to a sender is implemented: the accumulating builders
 * record and answer {@code this}, and everything else refuses loudly instead of quietly answering
 * something a test might believe.
 */
public class TestLocaleMessage implements ILocaleMessageBase {

    private final String text;
    private final Map<String, Object> placeholders = new LinkedHashMap<>();
    private int sendCount = 0;

    public TestLocaleMessage(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    /** How many senders this message was delivered to, across every {@link #send} call. */
    public int sendCount() {
        return sendCount;
    }

    public @Nullable Object placeholder(String key) {
        return placeholders.get(key);
    }

    @Override
    public void send(FCommandSender... commandSenders) {
        for (FCommandSender sender : commandSenders) {
            sender.sendMessage(Component.text(text));
            sendCount++;
        }
    }

    @Override
    public void broadcast() {
        throw new UnsupportedOperationException("TestLocaleMessage does not broadcast");
    }

    @Override
    public ILocaleMessageBase setHover(String hover) {
        return this;
    }

    @Override
    public ILocaleMessageBase setClick(String clickActionText, ClickActionType actionType) {
        return this;
    }

    @Override
    public ILocaleMessageBase addPlaceholder(String key, Object value) {
        placeholders.put(key, value);
        return this;
    }

    @Override
    public ILocaleMessageBase addPlaceholder(String key, Supplier<?> value) {
        placeholders.put(key, value);
        return this;
    }

    @Override
    public ILocaleMessageBase addPlaceholder(String key, Function<PlayerData, ?> value) {
        placeholders.put(key, value);
        return this;
    }

    @Override
    public ILocaleMessageBase addPlaceholders(Map<String, ?> values) {
        placeholders.putAll(values);
        return this;
    }

    @Override
    public ILocaleMessageBase addParser(String key, String description, Function<RenderContext, ?> parser) {
        throw new UnsupportedOperationException("TestLocaleMessage has no parsers");
    }

    @Override
    public ILocaleMessageBase addReplacer(CompoundReplacer compoundReplacer) {
        throw new UnsupportedOperationException("TestLocaleMessage has no replacers");
    }

    @Override
    public ILocaleMessageBase append(LocaleMessage localeMessage) {
        throw new UnsupportedOperationException("TestLocaleMessage does not append");
    }

    @Override
    public ILocaleMessageBase append(SendCustom sendCustom) {
        throw new UnsupportedOperationException("TestLocaleMessage does not append");
    }

    @Override
    public ILocaleMessageBase append(FancyText fancyText) {
        throw new UnsupportedOperationException("TestLocaleMessage does not append");
    }

    @Override
    public ILocaleMessageBase append(String text) {
        throw new UnsupportedOperationException("TestLocaleMessage does not append");
    }

    @Override
    public FancyText getFancyText(@Nullable FCommandSender sender) {
        return FancyText.of(text);
    }

    @Override
    public String toString() {
        return "TestLocaleMessage[" + text + "]";
    }
}
