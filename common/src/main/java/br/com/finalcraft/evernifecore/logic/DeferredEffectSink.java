package br.com.finalcraft.evernifecore.logic;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Where a delayed or repeated effect goes when it fires and its player is gone. The default discards it with an
 * {@code DEFERRED_DROPPED} audit entry; {@code Deliveries.start()} registers one that keeps the core's own effects as a
 * delivery the player claims on login. Never applies the effect on an offline player. Installed with
 * {@link DeferredEffects#register}.
 */
public interface DeferredEffectSink {

    /**
     * @param effect the parsed effect, for the audit text
     * @param node   the effect's own node, the modifiers that already ran ({@code delay}, {@code every}, {@code repeat},
     *               {@code target}, {@code chance}, {@code if}) taken out: what a delivery keeps and replays
     */
    void defer(ExecutionContext ctx, Effect effect, JsonNode node, UUID absentPlayer);
}
