package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The value behind a {@code ${key}}, computed only when the text being rendered actually cites the
 * key. A {@code null} result means "no value for this recipient": the token is left as written
 * instead of being replaced.
 */
@FunctionalInterface
public interface PlaceholderValue {

    @Nullable Object resolve(RenderContext context);

    static PlaceholderValue constant(@Nullable Object value) {
        return context -> value;
    }

    static PlaceholderValue lazy(Supplier<?> supplier) {
        return context -> supplier.get();
    }

    static PlaceholderValue perPlayer(Function<PlayerData, ?> function) {
        return context -> context.getPlayerData() == null ? null : function.apply(context.getPlayerData());
    }
}
