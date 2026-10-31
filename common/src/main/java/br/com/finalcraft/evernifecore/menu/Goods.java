package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The side of an {@link Offer} that is not the price: what the player gets on a BUY, what they hand over on a SELL. The
 * core ships {@link ItemGoods} and {@link EffectGoods}; a plugin decorates them (an item re-labelled per buyer) or writes
 * its own. Called on the trading thread.
 */
public interface Goods {

    /** Units the context's player holds - what a SELL can take; 0 for goods nobody holds. */
    int count(ExecutionContext ctx);

    /** Units that fit right now; {@link Integer#MAX_VALUE} for goods that take no room. */
    int roomFor(ExecutionContext ctx);

    /** What {@link Quantity#STACK} means for these goods; 1 for effects. */
    int getStackSize();

    /**
     * Hands {@code quantity} units over, to {@code to} when given, else the player's inventory; what does not fit
     * becomes a pending delivery.
     *
     * @return the pending delivery being written, or null when everything was handed over
     */
    @Nullable CompletableFuture<Delivery> give(ExecutionContext ctx, int quantity, @Nullable DeliveryDestination to);

    /** @throws IllegalStateException when the player holds fewer, nothing taken */
    void take(ExecutionContext ctx, int quantity);

    /** {@code quantity} units in words: "3x Diamond". */
    FancyText describe(ExecutionContext ctx, int quantity);

    /** False for goods that cannot be handed over by the player (effects). */
    boolean canBeSold();

    /** What a button shows for these goods; null when the menu must give it an icon. */
    default @Nullable ItemSpec getIcon() {
        return null;
    }

    /** Whether {@code item} is a unit of these goods - what a catalog finds a SELL offer by. */
    default boolean matches(ItemSpec item) {
        return false;
    }

    /** {@code item} as goods, one unit being {@code item.getAmount()} items, matched exactly (material, name and lore). */
    static ItemGoods ofItem(ItemSpec item) {
        return new ItemGoods(item, ItemMatcher.exact(item));
    }

    /** {@code item} as goods, what the player hands over being matched by {@code matcher}. */
    static ItemGoods ofItem(ItemSpec item, ItemMatcher matcher) {
        return new ItemGoods(item, matcher);
    }

    /** Effects run once per unit bought; cannot be sold. */
    static EffectGoods ofEffects(List<Effect> effects) {
        return new EffectGoods(null, effects);
    }

    /** {@link #ofEffects(List)} with how the goods read in a message: "VIP for 30 days". */
    static EffectGoods ofEffects(String description, List<Effect> effects) {
        return new EffectGoods(description, effects);
    }
}
