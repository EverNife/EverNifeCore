package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.EffectApplyEvent;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.types.PendingDefinition;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The effect types, the parser that reads an effect with its modifiers, and {@link #apply} - the one place an effect
 * is applied, always on its target's thread.
 */
public final class Effects {

    private static final TypeRegistry<Effect> REGISTRY = TypeRegistry.of(Effect.class, "effect")
            .reserveKeys(EffectModifiers.KEYS.toArray(new String[0]));

    private Effects() {
    }

    public static TypeRegistry<Effect> registry() {
        return REGISTRY;
    }

    public static EffectList of(List<Effect> effects) {
        return new EffectList(effects);
    }

    /**
     * Reads {@code node} as one effect of {@code caller}, with its modifiers. A type nobody registered yet keeps it
     * pending (it is audited as failed until the type arrives).
     *
     * @throws TypeRegistryException when the definition or a modifier does not read, {@code as} is written on a type
     * that takes no sender, or {@code scope: held} on a type that cannot be held
     */
    public static Effect parse(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        PendingDefinition<Effect> definition = REGISTRY.parseLater(caller, node, zone);
        Effect effect = definition.isResolved() ? definition.get().get() : new Pending(definition);
        String type = node.has(TypeRegistry.TYPE_FIELD) ? node.get(TypeRegistry.TYPE_FIELD).asText() : String.valueOf(node.fieldNames().next());
        EffectModifiers modifiers = EffectModifiers.parse(caller, type, node, zone);
        if (definition.isResolved()) {
            if (modifiers.getSender() != null && !effect.acceptsSender()) {
                throw new TypeRegistryException("The effect '" + type + "' writes as: " + modifiers.getSender() + ", but it runs no"
                        + " command, so it has no sender to choose. Remove as:, or use an effect that takes it, like command.");
            }
            if (modifiers.getScope() == EffectScope.HELD && !effect.isHoldable()) {
                throw new TypeRegistryException("The effect '" + type + "' writes scope: held, but it cannot be held and given back:"
                        + " only gamemode, fly, potion-effect, player-time and player-weather can. Use it in effects-on-enter, or"
                        + " write scope: permanent.");
            }
        }
        return modifiers.isNone() ? effect : new ModifiedEffect(effect, modifiers);
    }

    /** The type {@code effect} (as {@link #parse} gave it) still waits for, or null when it reads - it resolves by itself. */
    public static @Nullable String getPendingType(Effect effect) {
        Effect inner = effect instanceof ModifiedEffect ? ((ModifiedEffect) effect).getEffect() : effect;
        return inner instanceof Pending ? ((Pending) inner).definition.getMissingType() : null;
    }

    /** Reads a list of effects (a single node reads as a list of one); each keeps its own pending state. */
    public static EffectList parseList(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        if (node == null || node.isNull()) {
            return new EffectList(Collections.<Effect>emptyList());
        }
        List<Effect> effects = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode each : node) {
                effects.add(parse(caller, each, zone));
            }
        } else {
            effects.add(parse(caller, node, zone));
        }
        return new EffectList(effects);
    }

    /**
     * Applies {@code effect} for {@code ctx} on the target's thread: in place when the caller already is on the
     * player's world thread (on the main thread without a player), else queued there - the world's executor on Hytale,
     * the main thread on Bukkit. A failure is audited as {@code EFFECT_FAILED} and logged, never thrown. Any thread.
     */
    public static void apply(ExecutionContext ctx, Effect effect) {
        FPlayer player = ctx.getPlayer();
        FLocation location = player == null ? null : player.getLocation();
        if (location == null) {
            ECScheduler.onMainThread(() -> execute(ctx, effect));
            return;
        }
        IPlatform platform = EverNifeCore.getPlatform();
        String world = location.getWorldName();
        if (platform.isWorldThread(world)) {
            execute(ctx, effect);
        } else {
            platform.runOnWorldThread(world, () -> execute(ctx, effect));
        }
    }

    /**
     * Runs {@code effect} here, on a thread that is already the target's. A plain effect is announced by the
     * cancellable {@link EffectApplyEvent} first; a list and a modified effect pass through to what they hold.
     */
    static void execute(ExecutionContext ctx, Effect effect) {
        Effect running = effect;
        if (!(effect instanceof EffectList) && !(effect instanceof ModifiedEffect)) {
            EffectApplyEvent event = ECEventBus.global().postIfListened(EffectApplyEvent.class, () -> new EffectApplyEvent(ctx, effect));
            if (event != null) {
                if (event.isCancelled()) {
                    return;
                }
                running = event.getEffect();
            }
        }
        try {
            running.apply(ctx);
            if (!(running instanceof EffectList) && !(running instanceof ModifiedEffect)) {
                ctx.getAudit().add(ctx, AuditKind.EFFECT_APPLIED, String.valueOf(running));
            }
        } catch (RuntimeException failure) {
            String text = running + " failed: " + failure.getMessage();
            ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, text, null, failure);
            ctx.getCaller().getPlugin().getLog().warning("[" + ctx.getCaller() + "] " + text);
        }
    }

    private static final class Pending implements Effect {
        private final PendingDefinition<Effect> definition;

        Pending(PendingDefinition<Effect> definition) {
            this.definition = definition;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            if (!definition.isResolved()) {
                throw new IllegalStateException(String.valueOf(definition.getCause()));
            }
            definition.get().get().apply(ctx);
        }

        @Override
        public boolean isHoldable() {
            return definition.isResolved() && definition.get().get().isHoldable();
        }

        @Override
        public boolean acceptsSender() {
            return definition.isResolved() && definition.get().get().acceptsSender();
        }

        @Override
        public String toString() {
            return definition.isResolved() ? definition.get().get().toString() : "pending " + definition.getRaw();
        }
    }
}
