package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagDefinition;
import br.com.finalcraft.evernifecore.flag.FlagInteraction;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagResolvers;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.logic.Effect;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code effects-while-inside}: holdable effects (game mode, flight, potion effects, player time and weather) held on a
 * player while the value applies and given back when it stops - leaving, quitting, or the value going away. The slot
 * is the one the slot flags share ({@code game-mode}, {@code allow-fly}, ...): the first holder saves what the player
 * had, the last gives it back. An effect that cannot be held is refused when the value is read.
 */
public final class EffectsWhileInsideFlag extends Flag<List<FlagDefinition<Effect>>> {

    private static final String NAME = "effects-while-inside";
    private static final String RULE = "the first holder of a slot saves what the player had, the last gives it back";

    public EffectsWhileInsideFlag() {
        super(FlagKey.of(CoreFlags.OWNER, NAME), FlagTypes.effects(FlagKey.of(CoreFlags.OWNER, NAME), true),
                Collections.singleton(EventSource.MOVE), null);
    }

    @Override
    public String getDescription() {
        return "Holdable effects - gamemode, fly, potion-effect, player-time, player-weather - held while the player is inside and"
                + " given back when they leave.";
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public void onEnter(SessionContext context, List<FlagDefinition<Effect>> value) {
        EffectsFlag.apply(this, context, value, true);
    }

    @Override
    public void onLeave(SessionContext context, @Nullable List<FlagDefinition<Effect>> lastValue) {
        context.getSession().getHeldState().releaseAll(this, context.getPlayer());
    }

    @Override
    public void onRevalidate(SessionContext context, @Nullable List<FlagDefinition<Effect>> oldValue,
                             @Nullable List<FlagDefinition<Effect>> newValue) {
        if (newValue != null && newValue.equals(oldValue)) {
            return;
        }
        onLeave(context, oldValue);
        if (newValue != null) {
            onEnter(context, newValue);
        }
    }

    /** Every layer's effects are held together, in layer order. */
    @Override
    public FlagResolver<List<FlagDefinition<Effect>>> getResolver() {
        return FlagResolvers.concat();
    }

    @Override
    public List<FlagResolver<List<FlagDefinition<Effect>>>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
    }

    @Override
    public Set<String> getChannels() {
        return new LinkedHashSet<>(Arrays.asList("slot:game-mode", "slot:flight", "slot:time", "slot:weather", "slot:effects"));
    }

    @Override
    public Set<FlagInteraction> getInteractions() {
        Set<FlagInteraction> interactions = new LinkedHashSet<>();
        for (String slotFlag : Arrays.asList("game-mode", "allow-fly", "force-fly", "time-lock", "weather-lock", "potion-effects")) {
            interactions.add(FlagInteraction.with(FlagKey.of(CoreFlags.OWNER, slotFlag), RULE));
        }
        return interactions;
    }
}
