package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.AmountEvery;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagInteraction;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagResolvers;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.flag.TickingFlag;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code heal}, {@code feed}, {@code damage-over-time} and {@code hunger-over-time}: amounts applied to a player once per
 * interval ({@code 2 every 5s}, or a list of them), the first one interval after the value starts to apply. Layers of the
 * same interval add up ({@link FlagResolvers#sumByInterval()}); distinct intervals run each on its own tick.
 *
 * <p>A gain and its loss on the same vital - {@code heal} and {@code damage-over-time}, {@code feed} and
 * {@code hunger-over-time} - share one schedule per interval: whichever ticks first applies ONE net change for both, so
 * {@code heal 100} and {@code damage-over-time 2} of one interval heal 98. {@code invincible = TRUE} for the player drops
 * the damage part. Healing and feeding stop at full.</p>
 */
public final class AmountEveryFlag extends Flag<List<AmountEvery>> implements TickingFlag<List<AmountEvery>> {

    /** What the amount does. */
    public enum Effect {
        HEAL(IPlayerStateAccess.Capability.HEALTH, "damage-over-time", true),
        FEED(IPlayerStateAccess.Capability.FOOD, "hunger-over-time", true),
        DAMAGE(IPlayerStateAccess.Capability.HEALTH, "heal", false),
        HUNGER(IPlayerStateAccess.Capability.FOOD, "feed", false);

        private final IPlayerStateAccess.Capability capability;
        private final String sibling;
        private final boolean gain;

        Effect(IPlayerStateAccess.Capability capability, String sibling, boolean gain) {
            this.capability = capability;
            this.sibling = sibling;
            this.gain = gain;
        }

        String channel() {
            return capability == IPlayerStateAccess.Capability.HEALTH ? "vital:health" : "vital:food";
        }
    }

    /** By vital, by player: when each interval is due next - shared by the gain and the loss of that vital. */
    private static final Map<String, Map<UUID, Map<Duration, Instant>>> SCHEDULES = new ConcurrentHashMap<>();

    private final Effect effect;
    private final String description;

    public AmountEveryFlag(String name, Effect effect, String description) {
        super(FlagKey.of(CoreFlags.OWNER, name), FlagTypes.AMOUNT_EVERY_LIST, new HashSet<>(Arrays.asList(EventSource.MOVE, EventSource.TICK)), null);
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

    /** It acts on the player: a party's buff carries it wherever they stand. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public FlagResolver<List<AmountEvery>> getResolver() {
        return FlagResolvers.sumByInterval();
    }

    @Override
    public List<FlagResolver<List<AmountEvery>>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
    }

    @Override
    public Set<String> getChannels() {
        return Collections.singleton(effect.channel());
    }

    @Override
    public Set<FlagInteraction> getInteractions() {
        return effect.gain
                ? Collections.singleton(FlagInteraction.with(FlagKey.of(CoreFlags.OWNER, effect.sibling),
                        "the two add up to one net change per shared interval"))
                : Collections.emptySet();
    }

    @Override
    public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
        return Collections.singleton(effect.capability);
    }

    @Override
    public void onEnter(SessionContext context, List<AmountEvery> value) {
        Map<Duration, Instant> due = schedule(context.getPlayer().getUniqueId());
        Instant now = ECClock.now();
        for (AmountEvery entry : value) {
            due.putIfAbsent(entry.getInterval(), now.plus(entry.getInterval()));
        }
    }

    @Override
    public void onRevalidate(SessionContext context, @Nullable List<AmountEvery> oldValue, @Nullable List<AmountEvery> newValue) {
        if (newValue == null) {
            onLeave(context, oldValue);
            return;
        }
        if (oldValue != null) {
            forget(context, oldValue, newValue);
        }
        onEnter(context, newValue);
    }

    @Override
    public void onLeave(SessionContext context, List<AmountEvery> lastValue) {
        forget(context, lastValue, Collections.emptyList());
    }

    /** Drops the schedule of the intervals of {@code gone} that neither {@code kept} nor the sibling still runs. */
    private void forget(SessionContext context, List<AmountEvery> gone, List<AmountEvery> kept) {
        UUID uuid = context.getPlayer().getUniqueId();
        Map<Duration, Instant> due = schedule(uuid);
        Set<Duration> running = intervals(kept, sibling(context));
        for (AmountEvery entry : gone) {
            if (!running.contains(entry.getInterval())) {
                due.remove(entry.getInterval());
            }
        }
        if (due.isEmpty()) {
            SCHEDULES.get(effect.channel()).remove(uuid);
        }
    }

    @Override
    public void onTick(SessionContext context, List<AmountEvery> value) {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        if (!access.supports(effect.capability)) {
            return;
        }
        FPlayer player = context.getPlayer();
        List<AmountEvery> sibling = sibling(context);
        List<AmountEvery> gains = effect.gain ? value : sibling;
        List<AmountEvery> losses = effect.gain ? sibling : value;
        boolean invincible = effect.capability == IPlayerStateAccess.Capability.HEALTH && isInvincible(context);
        Map<Duration, Instant> due = schedule(player.getUniqueId());
        Instant now = ECClock.now();
        for (Duration interval : intervals(value, sibling)) {
            Instant at = due.get(interval);
            if (at == null) {
                due.put(interval, now.plus(interval)); //ticked before the session entered it: start counting
                continue;
            }
            if (now.isBefore(at)) {
                continue;
            }
            due.put(interval, now.plus(interval));
            int net = amountAt(gains, interval) - (invincible ? 0 : amountAt(losses, interval));
            if (net != 0) {
                apply(context, access, player, net);
            }
        }
    }

    private void apply(SessionContext context, IPlayerStateAccess access, FPlayer player, int net) {
        if (effect.capability == IPlayerStateAccess.Capability.FOOD) {
            access.setFood(player, Math.max(0, Math.min(access.getMaxFood(player), access.getFood(player) + net)));
            return;
        }
        if (net > 0) {
            double health = access.getHealth(player);
            if (health > 0) { //the dead are not healed back
                access.setHealth(player, Math.min(access.getMaxHealth(player), health + net));
            }
            return;
        }
        Flag<?> damage = effect == Effect.DAMAGE ? this : FlagRegistry.get().get(FlagKey.of(CoreFlags.OWNER, effect.sibling));
        FlagSettings settings = damage == null ? null : context.getSettings(damage);
        access.damage(player, -net, settings instanceof DamageOverTimeSettings && ((DamageOverTimeSettings) settings).isIgnoreArmor());
    }

    /** What the sibling (the other side of this vital) applies to the player now; empty when nothing. */
    @SuppressWarnings("unchecked")
    private List<AmountEvery> sibling(SessionContext context) {
        Flag<?> sibling = FlagRegistry.get().get(FlagKey.of(CoreFlags.OWNER, effect.sibling));
        Object applied = sibling == null ? null : context.getSession().getAppliedSessionValue(sibling);
        return applied instanceof List ? (List<AmountEvery>) applied : Collections.emptyList();
    }

    private static boolean isInvincible(SessionContext context) {
        Tristate invincible = RegionLookup.get().resolution(CoreFlags.INVINCIBLE, context.getSession().getCurrent(), context.getPlayer()).getValue();
        return invincible == Tristate.TRUE;
    }

    private Map<Duration, Instant> schedule(UUID player) {
        return SCHEDULES.computeIfAbsent(effect.channel(), channel -> new ConcurrentHashMap<>())
                .computeIfAbsent(player, uuid -> new ConcurrentHashMap<>());
    }

    private static Set<Duration> intervals(List<AmountEvery> one, List<AmountEvery> other) {
        Set<Duration> intervals = new LinkedHashSet<>();
        for (AmountEvery entry : one) {
            intervals.add(entry.getInterval());
        }
        for (AmountEvery entry : other) {
            intervals.add(entry.getInterval());
        }
        return intervals;
    }

    private static int amountAt(List<AmountEvery> entries, Duration interval) {
        int amount = 0;
        for (AmountEvery entry : entries) {
            if (entry.getInterval().equals(interval)) {
                amount += entry.getAmount();
            }
        }
        return amount;
    }
}
