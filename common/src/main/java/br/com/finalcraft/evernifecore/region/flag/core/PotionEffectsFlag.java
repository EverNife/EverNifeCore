package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.TickingFlag;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.ActiveEffect;
import br.com.finalcraft.evernifecore.region.playerstate.HeldPlayerState;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gives whoever stands in the region its effects, renewed by the region tick while they stay, and on the way out takes
 * only those it gave: an effect of the same kind the player already had comes back as it was, any other stays untouched.
 * An effect id the server does not know is skipped.
 */
public final class PotionEffectsFlag extends RegionFlag<List<EffectLevel>> implements TickingFlag<List<EffectLevel>> {

    /** How long each given effect lasts; the tick renews it once under {@link #RENEW_UNDER}, so it never runs out inside. */
    static final Duration LENGTH = Duration.ofSeconds(30);
    static final Duration RENEW_UNDER = Duration.ofSeconds(15);

    public PotionEffectsFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "potion-effects"), FlagTypes.EFFECT_LIST,
                new HashSet<>(Arrays.asList(EventSource.MOVE, EventSource.TICK)), null);
    }

    @Override
    public String getDescription() {
        return "The effects whoever stands in the region has, as effect:level separated by commas (speed:2, night_vision); on the way"
                + " out only the effects the region gave are taken.";
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    @Override
    public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
        return Collections.singleton(IPlayerStateAccess.Capability.EFFECTS);
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return PlayerStateSettings.class;
    }

    /** The first. */
    @Override
    public List<EffectLevel> resolveTie(List<List<EffectLevel>> values) {
        return values.get(0);
    }

    @Override
    public void onEnter(SessionContext context, List<EffectLevel> value) {
        IPlayerStateAccess access = access();
        if (access == null) {
            return;
        }
        PlayerStateSettings settings = context.getRegion().getEffectiveFlagSettings(this);
        HeldPlayerState held = context.getSession().getHeldState();
        for (EffectLevel effect : value) {
            if (access.isEffect(effect.getEffect())) {
                held.hold(HeldPlayerState.effect(effect.getEffect()), this, effect, settings.isPersistent(), context.getPlayer());
                access.addEffect(context.getPlayer(), effect, LENGTH);
            }
        }
    }

    @Override
    public void onRevalidate(SessionContext context, @Nullable List<EffectLevel> oldValue, @Nullable List<EffectLevel> newValue) {
        if (oldValue != null) {
            for (EffectLevel effect : oldValue) {
                if (newValue == null || newValue.stream().noneMatch(kept -> kept.getEffect().equals(effect.getEffect()))) {
                    give(context, effect);
                }
            }
        }
        if (newValue != null) {
            onEnter(context, newValue);
        }
    }

    @Override
    public void onLeave(SessionContext context, List<EffectLevel> lastValue) {
        for (EffectLevel effect : lastValue) {
            give(context, effect);
        }
    }

    /** Renews each effect the player is about to lose, or lost to milk or a plugin. */
    @Override
    public void onTick(SessionContext context, List<EffectLevel> value) {
        IPlayerStateAccess access = access();
        if (access == null) {
            return;
        }
        FPlayer player = context.getPlayer();
        for (EffectLevel effect : value) {
            if (!access.isEffect(effect.getEffect())) {
                continue;
            }
            ActiveEffect active = access.getEffect(player, effect.getEffect());
            if (active == null || !active.getEffect().equals(effect) || active.getRemaining().compareTo(RENEW_UNDER) < 0) {
                access.addEffect(player, effect, LENGTH);
            }
        }
    }

    private void give(SessionContext context, EffectLevel effect) {
        if (access() != null) {
            context.getSession().getHeldState().release(HeldPlayerState.effect(effect.getEffect()), this, context.getPlayer());
        }
    }

    private static @Nullable IPlayerStateAccess access() {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        return access.supports(IPlayerStateAccess.Capability.EFFECTS) ? access : null;
    }
}
