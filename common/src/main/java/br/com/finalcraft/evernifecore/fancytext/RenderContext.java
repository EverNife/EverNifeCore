package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

import java.util.function.Function;

/**
 * Everything the placeholder engine needs for a single render: who is receiving the message, their
 * resolved {@link PlayerData} (looked up once, not once per placeholder key) and the command-scope
 * context that is open, if any.
 */
public final class RenderContext {

    public static final RenderContext EMPTY = new RenderContext(null, null, MessageContext.EMPTY);

    private final FCommandSender sender;
    private final PlayerData playerData;
    private final MessageContext messageContext;
    private final PlaceholderScope scope;

    public RenderContext(@Nullable FCommandSender sender,
                         @Nullable PlayerData playerData,
                         MessageContext messageContext) {
        this(sender, playerData, messageContext, null);
    }

    private RenderContext(@Nullable FCommandSender sender,
                          @Nullable PlayerData playerData,
                          MessageContext messageContext,
                          @Nullable PlaceholderScope scope) {
        this.sender = sender;
        this.playerData = playerData;
        this.messageContext = messageContext == null ? MessageContext.EMPTY : messageContext;
        this.scope = scope;
    }

    // The enclosing placeholder level travels with the render, so a leaf can fall back to the
    // values declared by the chain that contains it. Deriving instead of mutating keeps the shared
    // EMPTY constant free of any single render's state.
    RenderContext withScope(PlaceholderScope scope) {
        return new RenderContext(sender, playerData, messageContext, scope);
    }

    @Nullable PlaceholderScope getScope() {
        return scope;
    }

    public static RenderContext of(@Nullable FCommandSender sender) {
        if (sender == null) {
            return EMPTY;
        }
        PlayerData playerData = sender instanceof FPlayer
                ? PlayerController.getLoaded(sender.getUniqueId())
                : null;
        // A later phase replaces this constant with a lookup of the command scope that is open on
        // the current thread.
        return new RenderContext(sender, playerData, MessageContext.EMPTY);
    }

    public @Nullable FCommandSender getSender() {
        return sender;
    }

    public @Nullable PlayerData getPlayerData() {
        return playerData;
    }

    public MessageContext getMessageContext() {
        return messageContext;
    }

    /**
     * Resolves one entry of the untyped {@code placeholder -> Object} maps: a plain value renders as
     * itself, a {@link Function} renders against this render's PlayerData. Returns {@code null} when
     * a per-player value has no PlayerData to apply to, so the caller leaves the token untouched
     * rather than printing the function itself.
     */
    @SuppressWarnings("unchecked")
    public @Nullable String resolveMappedValue(@Nullable Object rawValue) {
        if (rawValue instanceof Function) {
            return playerData == null
                    ? null
                    : String.valueOf(((Function<PlayerData, Object>) rawValue).apply(playerData));
        }
        return String.valueOf(rawValue);
    }
}
