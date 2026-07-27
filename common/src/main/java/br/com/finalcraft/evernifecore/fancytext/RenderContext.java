package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private final Map<Object, Optional<Object>> resolvedOnce = new HashMap<>();

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
        return new RenderContext(sender, playerData, MessageScope.currentOrEmpty());
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
     * The value of one declaration in this render, computed at most once. {@code token} is the
     * identity of the declaration, so the same key declared by two different messages keeps two
     * answers.
     *
     * <p>Resolving to {@code null} is itself an answer worth remembering - otherwise the key would be
     * recomputed on every mention - which is what the {@link Optional} carrier is for.</p>
     */
    public @Nullable Object resolveOnce(Object token, Supplier<?> compute) {
        return resolvedOnce.computeIfAbsent(token, ignored -> Optional.ofNullable(compute.get()))
                .orElse(null);
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
