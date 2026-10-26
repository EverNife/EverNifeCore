package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagDefinition;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagResolvers;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.HoldableEffect;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code effects-on-enter} and {@code effects-on-exit}: effects of any plugin applied when a player enters or leaves
 * the region, in order. An effect whose plugin is absent stays as written and is skipped until it registers.
 */
public final class EffectsFlag extends Flag<List<FlagDefinition<Effect>>> {

    /** The attribute of the effects' context that carries the region the value came from (absent for none). */
    public static final String REGION_ATTRIBUTE = "region";

    private final boolean onExit;

    private EffectsFlag(String name, boolean onExit) {
        super(FlagKey.of(CoreFlags.OWNER, name), FlagTypes.effects(FlagKey.of(CoreFlags.OWNER, name), false),
                Collections.singleton(EventSource.MOVE), null);
        this.onExit = onExit;
    }

    public static EffectsFlag onEnter() {
        return new EffectsFlag("effects-on-enter", false);
    }

    public static EffectsFlag onExit() {
        return new EffectsFlag("effects-on-exit", true);
    }

    @Override
    public String getDescription() {
        return "Effects applied, in order, when a player " + (onExit ? "leaves" : "enters") + " the region: {type: message, text: ...},"
                + " {type: command, command: ...} or any plugin's effect.";
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    @Override
    public void onEnter(SessionContext context, List<FlagDefinition<Effect>> value) {
        if (!onExit) {
            apply(this, context, value, false);
        }
    }

    @Override
    public void onLeave(SessionContext context, List<FlagDefinition<Effect>> lastValue) {
        if (onExit) {
            apply(this, context, lastValue, false);
        }
    }

    /** Crossing straight from one region to another applies the entered one's on-enter, or the left one's on-exit. */
    @Override
    public void onRevalidate(SessionContext context, @Nullable List<FlagDefinition<Effect>> oldValue,
                             @Nullable List<FlagDefinition<Effect>> newValue) {
        if (oldValue == null || newValue == null || oldValue.equals(newValue)) {
            return; //an unchanged value asked again applies nothing
        }
        apply(this, context, onExit ? oldValue : newValue, false);
    }

    /** Overlapping regions each apply their own effects, in layer order. */
    @Override
    public FlagResolver<List<FlagDefinition<Effect>>> getResolver() {
        return FlagResolvers.concat();
    }

    @Override
    public List<FlagResolver<List<FlagDefinition<Effect>>>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
    }

    /**
     * Applies every effect of {@code effects} for the player of {@code session} in {@link #contextOf}; a pending one is
     * skipped.
     *
     * @param held hold each effect for {@code flag} ({@link HoldableEffect#HELD_BY_ATTRIBUTE}) instead of changing for good
     */
    static void apply(Flag<?> flag, SessionContext session, List<FlagDefinition<Effect>> effects, boolean held) {
        ExecutionContext ctx = contextOf(flag, session, held);
        for (FlagDefinition<Effect> effect : effects) {
            String pending = effect.getPendingType();
            if (pending != null) {
                ECDebugModule.REGIONS.debug("{} skips {} for {}: no plugin registers the effect type {} yet.", flag.getKey(), effect,
                        session.getPlayer().getName(), pending);
                continue;
            }
            Effects.apply(ctx, effect.get());
        }
    }

    /**
     * The context a session flag applies its effects in: the player of {@code session}, the caller the owner of {@code flag}
     * with the origin {@code region/<id>#<flag name>} ({@code actor#<flag name>} for a value no region gave), the region
     * under {@link #REGION_ATTRIBUTE}.
     */
    static ExecutionContext contextOf(Flag<?> flag, SessionContext session, boolean held) {
        String origin = (session.getRegion() == null ? "actor" : "region/" + session.getRegion().getId()) + "#" + flag.getKey().getName();
        return ExecutionContext.builder(Caller.of(flag.getOwner(), origin))
                .player(session.getPlayer())
                .attribute(REGION_ATTRIBUTE, session.getRegion())
                .attribute(HoldableEffect.HELD_BY_ATTRIBUTE, held ? flag : null)
                .build();
    }
}
