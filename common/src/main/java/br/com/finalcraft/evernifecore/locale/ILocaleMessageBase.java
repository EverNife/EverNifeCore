package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.*;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a message about to be sent can say about itself. It speaks the same vocabulary as
 * {@link FancyText}: {@code setX} replaces an attribute, {@code addX} accumulates, and
 * {@code append} builds a chain - so knowing one of the two is knowing both.
 */
public interface ILocaleMessageBase {

    void send(FCommandSender... commandSenders);

    default void send(List<FCommandSender> commandSenders) {
        send(commandSenders.toArray(new FCommandSender[0]));
    }

    /**
     * Sends carrying an explicit {@link RenderContext}, whose {@link CommandMessageContext} wins over the
     * command scope of the sending thread. Each recipient still gets their own render.
     */
    default void send(RenderContext context, FCommandSender... commandSenders) {
        for (FCommandSender sender : commandSenders) {
            getFancyText(sender).send(context, sender);
        }
    }

    /**
     * Sends only when {@code condition} holds, so a guarded send stops needing an {@code if} block.
     * Only the delivery is conditional: whatever was declared on this message is still evaluated, so
     * a value that is expensive or unsafe to compute still belongs behind a real {@code if}.
     */
    default void sendIf(boolean condition, FCommandSender... commandSenders) {
        if (condition) {
            send(commandSenders);
        }
    }

    /** Sends to every recipient of {@code IPlatformChatAdapter#getBroadcastAudience()}, console included. */
    void broadcast();

    ILocaleMessageBase setHover(String hover);

    default ILocaleMessageBase setHover(List<String> hover) {
        return setHover(String.join("\n", hover));
    }

    ILocaleMessageBase setClick(String clickActionText, ClickActionType actionType);

    default ILocaleMessageBase setClickCommand(String command) {
        return setClick(command, ClickActionType.RUN_COMMAND);
    }

    default ILocaleMessageBase setClickSuggest(String suggestion) {
        return setClick(suggestion, ClickActionType.SUGGEST_COMMAND);
    }

    default ILocaleMessageBase setClickLink(String url) {
        return setClick(url, ClickActionType.OPEN_URL);
    }

    /**
     * Declares the value of {@code ${key}} (case-insensitive) on this message. The key is taken
     * exactly as written, so it must be the bare name - {@code "saldo"}, never {@code "${saldo}"}.
     */
    ILocaleMessageBase addPlaceholder(String key, Object value);

    ILocaleMessageBase addPlaceholder(String key, Supplier<?> value);

    ILocaleMessageBase addPlaceholder(String key, Function<PlayerData, ?> value);

    ILocaleMessageBase addPlaceholders(Map<String, ?> values);

    ILocaleMessageBase addParser(String key, String description, Function<RenderContext, ?> parser);

    ILocaleMessageBase addReplacer(CompoundReplacer compoundReplacer);

    ILocaleMessageBase append(LocaleMessage localeMessage);

    ILocaleMessageBase append(SendCustom sendCustom);

    /** Appends a raw {@link FancyText}: the piece is copied on the way in, as everywhere else. */
    ILocaleMessageBase append(FancyText fancyText);

    ILocaleMessageBase append(String text);

    /**
     * Exactly what {@link #send(FCommandSender...)} would deliver to {@code sender}, without sending
     * it - a preview can never describe something else.
     */
    FancyText getFancyText(@Nullable FCommandSender sender);

}
