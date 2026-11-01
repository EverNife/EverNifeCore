package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** The one {@link DeferredEffectSink} in use. */
public final class DeferredEffects {

    private static final DeferredEffectSink DROP = (ctx, effect, node, absentPlayer) -> ctx.getAudit().add(ctx, AuditKind.DEFERRED_DROPPED,
            effect + " was dropped: its player " + absentPlayer + " left before it fired, and no plugin keeps deferred effects.");

    private static volatile DeferredEffectSink sink = DROP;

    private DeferredEffects() {
    }

    /** Replaces the sink; {@code null} goes back to the one that drops. */
    public static void register(@Nullable DeferredEffectSink newSink) {
        sink = newSink == null ? DROP : newSink;
    }

    public static DeferredEffectSink get() {
        return sink;
    }

    /** A deferred share of a reward: its node still says the whole amount, so it is dropped whatever the sink. */
    static void dropShare(ExecutionContext ctx, Effect effect, UUID absentPlayer) {
        ctx.getAudit().add(ctx, AuditKind.DEFERRED_DROPPED, effect + " was dropped: its player " + absentPlayer + " left before it fired, and a"
                + " share of a reward cannot wait as a delivery (its node says the whole amount).");
    }
}
