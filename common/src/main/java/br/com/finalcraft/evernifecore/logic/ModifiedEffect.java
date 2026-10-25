package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.time.ECScheduler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** An effect with the executor's modifiers around it ({@link EffectModifiers}). */
final class ModifiedEffect implements Effect {

    private final Effect effect;
    private final EffectModifiers modifiers;

    ModifiedEffect(Effect effect, EffectModifiers modifiers) {
        this.effect = effect;
        this.modifiers = modifiers;
    }

    Effect getEffect() {
        return effect;
    }

    EffectModifiers getModifiers() {
        return modifiers;
    }

    @Override
    public void apply(ExecutionContext ctx) {
        Condition condition = modifiers.getCondition();
        if (condition != null && !Conditions.test(condition, ctx).isPassed()) {
            return;
        }
        BigDecimal chance = modifiers.getChance();
        if (chance != null && BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble()).compareTo(chance) >= 0) {
            return;
        }
        if (modifiers.getScope() == EffectScope.HELD && !effect.isHoldable()) {
            throw new IllegalStateException(effect + " cannot be held (scope: held): use effects-on-enter, or scope: permanent.");
        }
        boolean self = modifiers.getTarget().equals("self");
        for (ExecutionContext target : targets(ctx)) {
            ExecutionContext run = modifiers.getSender() == null ? target
                    : target.derive().attribute(EffectModifiers.SENDER_ATTRIBUTE, modifiers.getSender()).build();
            long delay = modifiers.getDelay() == null ? 0 : modifiers.getDelay().toMillis();
            long every = modifiers.getEvery() == null ? 0 : modifiers.getEvery().toMillis();
            for (int i = 0; i < modifiers.getRepeat(); i++) {
                long at = delay + i * every;
                if (at > 0) {
                    ECScheduler.scheduleAsync(() -> fire(run), at);
                } else if (self) {
                    Effects.execute(run, effect); //already on this player's thread
                } else {
                    Effects.apply(run, effect); //another player: their world's thread
                }
            }
        }
    }

    /** A delayed run: the player is looked up again, and a player who left never gets the effect. */
    private void fire(ExecutionContext run) {
        FPlayer player = run.getPlayer();
        if (player == null) {
            Effects.apply(run, effect);
            return;
        }
        UUID id = player.getUniqueId();
        FPlayer now = EverNifeCore.getPlatform().getPlayer(id);
        if (now == null || !now.isOnline()) {
            DeferredEffects.get().defer(run, effect, id);
            return;
        }
        Effects.apply(run.derive().player(now).build(), effect);
    }

    private List<ExecutionContext> targets(ExecutionContext ctx) {
        String target = modifiers.getTarget();
        if (target.equals("self")) {
            return Collections.singletonList(ctx);
        }
        String kind = target.equals("group") ? "group" : target.equals("region-players") ? ProgressOwner.REGION : ProgressOwner.WORLD;
        ProgressOwnerProvider provider = ProgressOwners.find(kind).orElseThrow(() -> new IllegalStateException("target: " + target
                + " applies to the members of a " + kind + " owner, and no plugin registered a provider for that kind. The plugin"
                + " that owns " + kind + "s registers one with ProgressOwners.register(plugin, provider)."));
        ProgressOwner owner = ctx.getOwner();
        if (!owner.isKind(kind)) {
            FPlayer player = ctx.getPlayer();
            FLocation location = player == null ? null : player.getLocation();
            if (!kind.equals(ProgressOwner.WORLD) || location == null) {
                throw new IllegalStateException("target: " + target + " applies to the members of a " + kind + " owner, and the owner"
                        + " of this context is " + owner + ". Build the context with .owner(ProgressOwner." + kind + "(...)).");
            }
            owner = ProgressOwner.world(location.getWorldName());
        }
        List<ExecutionContext> contexts = new ArrayList<>();
        for (FPlayer member : provider.getMembers(owner)) {
            contexts.add(ctx.derive().player(member).build());
        }
        return contexts;
    }

    @Override
    public EffectScope getScope() {
        return modifiers.getScope();
    }

    @Override
    public boolean isHoldable() {
        return effect.isHoldable();
    }

    @Override
    public boolean acceptsSender() {
        return effect.acceptsSender();
    }

    @Override
    public Effect withShare(BigDecimal share) {
        return new ModifiedEffect(effect.withShare(share), modifiers);
    }

    @Override
    public String toString() {
        return effect.toString();
    }
}
