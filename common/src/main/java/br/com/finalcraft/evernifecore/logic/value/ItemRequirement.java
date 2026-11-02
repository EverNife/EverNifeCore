package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A {@link ContributionRequirement} of items, counted in and taken out of the depositing player's inventory (the
 * platform's {@code evernifecore:inventory}). Consuming unless {@link #check()}. Immutable; called on the deposit's
 * thread, which reaches the inventory on the player's thread.
 */
public final class ItemRequirement implements ContributionRequirement<ItemMatcher> {

    private final ItemMatcher matcher;
    private final BigDecimal target;
    private final boolean consuming;

    private ItemRequirement(ItemMatcher matcher, BigDecimal target, boolean consuming) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.target = Objects.requireNonNull(target, "target");
        this.consuming = consuming;
    }

    /** Collects {@code target} items {@code matcher} accepts, taken from each depositor. */
    public static ItemRequirement of(ItemMatcher matcher, int target) {
        return new ItemRequirement(matcher, BigDecimal.valueOf(target), true);
    }

    /** This requirement counting what the depositor holds, taking nothing. */
    public ItemRequirement check() {
        return new ItemRequirement(matcher, target, false);
    }

    @Override
    public ItemMatcher getSubject() {
        return matcher;
    }

    @Override
    public BigDecimal getTarget() {
        return target;
    }

    @Override
    public boolean isConsuming() {
        return consuming;
    }

    @Override
    public BigDecimal countHeld(ExecutionContext ctx) {
        return BigDecimal.valueOf(DeliveryDestinations.requireInventory().count(Contribution.depositor(ctx), matcher));
    }

    @Override
    public BigDecimal take(ExecutionContext ctx, BigDecimal upTo) {
        FPlayer player = Contribution.depositor(ctx);
        int amount = Math.min(DeliveryDestinations.requireInventory().count(player, matcher), upTo.intValue());
        if (amount <= 0) {
            return BigDecimal.ZERO;
        }
        return DeliveryDestinations.requireInventory().take(player, matcher, amount) ? BigDecimal.valueOf(amount) : BigDecimal.ZERO;
    }

    /** Into the inventory; what does not fit becomes a delivery for the player, as an item cost's refund does. */
    @Override
    public void giveBack(ExecutionContext ctx, BigDecimal amount) {
        ItemSpec rest = DeliveryDestinations.requireInventory().deliverItem(ctx, matcher.toItem(amount.intValue()));
        if (rest != null && rest.getAmount() > 0) {
            Deliveries.get().keepFor(ctx, DeliveryPolicy.KEEP_SELF_CONTAINED, null, PendingDestination.giveItemNode(rest, null));
        }
    }

    @Override
    public String toString() {
        return (consuming ? "" : "check ") + target.toPlainString() + " " + matcher;
    }
}
