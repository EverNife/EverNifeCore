package br.com.finalcraft.evernifecore.logic;

import java.util.UUID;

/**
 * Where a delayed or repeated effect goes when it fires and its player is gone. The default discards it with an
 * {@code DEFERRED_DROPPED} audit entry; a delivery system registers one that keeps what is self-contained. Never
 * applies the effect on an offline player. Installed with {@link DeferredEffects#register}.
 */
public interface DeferredEffectSink {

    void defer(ExecutionContext ctx, Effect effect, UUID absentPlayer);
}
