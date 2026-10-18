package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.RegionClock;
import br.com.finalcraft.evernifecore.region.flag.AmountEvery;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.TickingFlag;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code heal}, {@code feed}, {@code damage-over-time} and {@code hunger-over-time}: an amount applied to whoever stands in
 * the region once per interval ({@code 2 every 5s}), first one interval after entering. Healing and feeding stop at full.
 */
public final class AmountEveryFlag extends RegionFlag<AmountEvery> implements TickingFlag<AmountEvery> {

    /** What the amount does. */
    public enum Effect {
        HEAL(IPlayerStateAccess.Capability.HEALTH),
        FEED(IPlayerStateAccess.Capability.FOOD),
        DAMAGE(IPlayerStateAccess.Capability.HEALTH),
        HUNGER(IPlayerStateAccess.Capability.FOOD);

        private final IPlayerStateAccess.Capability capability;

        Effect(IPlayerStateAccess.Capability capability) {
            this.capability = capability;
        }
    }

    private final Effect effect;
    private final String description;
    private final Map<UUID, Instant> nextAt = new ConcurrentHashMap<>();

    public AmountEveryFlag(String name, Effect effect, String description) {
        super(FlagKey.of(CoreFlags.OWNER, name), FlagTypes.AMOUNT_EVERY, new HashSet<>(Arrays.asList(EventSource.MOVE, EventSource.TICK)), null);
        this.effect = effect;
        this.description = description;
    }

    public Effect getEffect() {
        return effect;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return effect == Effect.DAMAGE ? DamageOverTimeSettings.class : FlagSettings.class;
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    @Override
    public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
        return Collections.singleton(effect.capability);
    }

    /** The first. */
    @Override
    public AmountEvery resolveTie(List<AmountEvery> values) {
        return values.get(0);
    }

    @Override
    public void onEnter(SessionContext context, AmountEvery value) {
        nextAt.put(context.getPlayer().getUniqueId(), RegionClock.now().plus(value.getInterval()));
    }

    @Override
    public void onRevalidate(SessionContext context, @Nullable AmountEvery oldValue, @Nullable AmountEvery newValue) {
        if (newValue == null) {
            onLeave(context, oldValue);
        } else if (!newValue.equals(oldValue)) {
            onEnter(context, newValue);
        }
    }

    @Override
    public void onLeave(SessionContext context, AmountEvery lastValue) {
        nextAt.remove(context.getPlayer().getUniqueId());
    }

    @Override
    public void onTick(SessionContext context, AmountEvery value) {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        if (!access.supports(effect.capability)) {
            return;
        }
        FPlayer player = context.getPlayer();
        Instant now = RegionClock.now();
        Instant due = nextAt.get(player.getUniqueId());
        if (due == null) {
            nextAt.put(player.getUniqueId(), now.plus(value.getInterval())); //ticked before the session entered it: start counting
            return;
        }
        if (now.isBefore(due)) {
            return;
        }
        nextAt.put(player.getUniqueId(), now.plus(value.getInterval()));
        int amount = value.getAmount();
        switch (effect) {
            case HEAL:
                double health = access.getHealth(player);
                if (health > 0) { //the dead are not healed back
                    access.setHealth(player, Math.min(access.getMaxHealth(player), health + amount));
                }
                break;
            case FEED:
                access.setFood(player, Math.min(access.getMaxFood(player), access.getFood(player) + amount));
                break;
            case DAMAGE:
                DamageOverTimeSettings settings = context.getRegion().getEffectiveFlagSettings(this);
                access.damage(player, amount, settings.isIgnoreArmor());
                break;
            case HUNGER:
                access.setFood(player, Math.max(0, access.getFood(player) - amount));
                break;
        }
    }
}
