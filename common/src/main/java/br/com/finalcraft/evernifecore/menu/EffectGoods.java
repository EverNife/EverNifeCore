package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Effects as {@link Goods}: buying N units applies every effect N times, each through {@link Effects#apply} (the
 * target's thread). Nobody holds them, so they cannot be sold, and they take no room.
 */
public class EffectGoods implements Goods {

    private final @Nullable String description;
    private final List<Effect> effects;

    EffectGoods(@Nullable String description, List<Effect> effects) {
        if (effects == null || effects.isEmpty()) {
            throw new IllegalArgumentException("Effect goods run at least one effect: a command, a rank, an open-menu.");
        }
        this.description = description;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }

    public List<Effect> getEffects() {
        return effects;
    }

    @Override
    public int count(ExecutionContext ctx) {
        return 0;
    }

    @Override
    public int roomFor(ExecutionContext ctx) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getStackSize() {
        return 1;
    }

    /** Applies every effect {@code quantity} times; {@code to} means nothing to effects. */
    @Override
    public @Nullable CompletableFuture<Delivery> give(ExecutionContext ctx, int quantity, @Nullable DeliveryDestination to) {
        for (int i = 0; i < quantity; i++) {
            for (Effect effect : effects) {
                Effects.apply(ctx, effect);
            }
        }
        return null;
    }

    @Override
    public void take(ExecutionContext ctx, int quantity) {
        throw new IllegalStateException("Effects cannot be taken back from a player: " + this + " is bought only.");
    }

    @Override
    public FancyText describe(ExecutionContext ctx, int quantity) {
        String what = description != null ? ctx.resolve(description) : effects.toString();
        return FancyText.of(quantity == 1 ? what : quantity + "x " + what);
    }

    @Override
    public boolean canBeSold() {
        return false;
    }

    @Override
    public String toString() {
        return "EffectGoods{" + (description != null ? description : effects) + "}";
    }
}
