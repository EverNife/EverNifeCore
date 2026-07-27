package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Everything the placeholder engine needs for a single render: who is receiving the message, their
 * resolved {@link PlayerData} (looked up once, not once per placeholder key), the command-scope
 * context that is open, and what each declaration has already resolved to in this render.
 */
public final class RenderContext {

    private final FCommandSender sender;
    private final PlayerData playerData;
    private final CommandMessageContext commandMessageContext;
    private final List<MessagePlaceholders> inherited;
    // Shared with every context derived from this one: derivation happens WITHIN a render, so the
    // whole render must agree on what a declaration resolved to.
    private final Map<Object, Optional<Object>> resolvedOnce;

    public RenderContext(@Nullable FCommandSender sender,
                         @Nullable PlayerData playerData,
                         CommandMessageContext commandMessageContext) {
        this.sender = sender;
        this.playerData = playerData;
        this.commandMessageContext = commandMessageContext == null ? CommandMessageContext.EMPTY : commandMessageContext;
        this.inherited = Collections.emptyList();
        this.resolvedOnce = new HashMap<>();
    }

    private RenderContext(RenderContext parent, List<MessagePlaceholders> inherited) {
        this.sender = parent.sender;
        this.playerData = parent.playerData;
        this.commandMessageContext = parent.commandMessageContext;
        this.inherited = inherited;
        this.resolvedOnce = parent.resolvedOnce;
    }

    /**
     * A context of its own, for a render with no recipient. This is a factory and not a shared
     * constant on purpose: a context now remembers what it resolved, and one instance handed to
     * every render would show the previous recipient's values to the next one.
     */
    public static RenderContext empty() {
        return new RenderContext(null, null, CommandMessageContext.EMPTY);
    }

    public static RenderContext of(@Nullable FCommandSender sender) {
        return of(sender, MessageScope.currentOrEmpty());
    }

    /**
     * A context for {@code sender} carrying an explicitly chosen {@link CommandMessageContext} instead of
     * whatever command scope happens to be open on this thread - which is the only way a message
     * delivered from an asynchronous task can still answer for {@code ${label}}.
     */
    public static RenderContext of(@Nullable FCommandSender sender, @Nullable CommandMessageContext commandMessageContext) {
        if (sender == null) {
            return new RenderContext(null, null, commandMessageContext);
        }
        PlayerData playerData = sender instanceof FPlayer
                ? PlayerController.getLoaded(sender.getUniqueId())
                : null;
        return new RenderContext(sender, playerData, commandMessageContext);
    }

    /**
     * This context aimed at one recipient: the command context is kept, everything that is specific
     * to who is reading (their PlayerData, and what this render has already resolved) is theirs
     * alone. This is how one explicitly built context serves a send to several recipients without
     * showing any of them another one's values.
     */
    public RenderContext forRecipient(@Nullable FCommandSender recipient) {
        return of(recipient, commandMessageContext);
    }

    // The enclosing placeholder level travels with the render, so a piece can fall back to what the
    // chain containing it declared. Innermost first: the closer level shadows the further one.
    RenderContext inheriting(MessagePlaceholders placeholders) {
        List<MessagePlaceholders> derived = new ArrayList<>(inherited.size() + 1);
        derived.add(placeholders);
        derived.addAll(inherited);
        return new RenderContext(this, Collections.unmodifiableList(derived));
    }

    /** The enclosing declaration levels, nearest first; empty for a piece that stands on its own. */
    List<MessagePlaceholders> getInherited() {
        return inherited;
    }

    public @Nullable FCommandSender getSender() {
        return sender;
    }

    public @Nullable PlayerData getPlayerData() {
        return playerData;
    }

    public CommandMessageContext getMessageContext() {
        return commandMessageContext;
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
}
