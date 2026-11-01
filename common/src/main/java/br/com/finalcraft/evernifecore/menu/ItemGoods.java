package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An item as {@link Goods}: one unit is {@code item.getAmount()} items. Reached through the platform's
 * {@code evernifecore:inventory} - the same primitives the {@code item} cost uses - and matched by an {@link ItemMatcher}
 * on what the player hands over.
 */
public class ItemGoods implements Goods {

    /** A stack of most items, on both platforms; {@link #withStackSize} for the ones that stack less. */
    public static final int DEFAULT_STACK = 64;

    private final ItemSpec item;
    private final ItemMatcher matcher;
    private final int stackSize;

    ItemGoods(ItemSpec item, ItemMatcher matcher) {
        this(item, matcher, Math.max(1, DEFAULT_STACK / item.getAmount()));
    }

    private ItemGoods(ItemSpec item, ItemMatcher matcher, int stackSize) {
        this.item = Objects.requireNonNull(item, "item");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.stackSize = stackSize;
    }

    /** These goods with {@code units} as what {@link Quantity#STACK} buys. */
    public ItemGoods withStackSize(int units) {
        if (units < 1) {
            throw new IllegalArgumentException("A stack holds at least 1 unit, not " + units + ".");
        }
        return new ItemGoods(item, matcher, units);
    }

    public ItemSpec getItem() {
        return item;
    }

    public ItemMatcher getMatcher() {
        return matcher;
    }

    @Override
    public int count(ExecutionContext ctx) {
        FPlayer player = ctx.getPlayer();
        return player == null ? 0 : DeliveryDestinations.requireInventory().count(player, matcher) / item.getAmount();
    }

    @Override
    public int roomFor(ExecutionContext ctx) {
        FPlayer player = ctx.getPlayer();
        return player == null ? 0 : DeliveryDestinations.requireInventory().roomFor(player, item.withAmount(1)) / item.getAmount();
    }

    @Override
    public int getStackSize() {
        return stackSize;
    }

    @Override
    public @Nullable CompletableFuture<Delivery> give(ExecutionContext ctx, int quantity, @Nullable DeliveryDestination to) {
        DeliveryDestination destination = to != null ? to : DeliveryDestinations.requireInventory();
        ItemSpec rest = destination.deliverItem(ctx, item.withAmount(quantity * item.getAmount()));
        if (rest == null || rest.getAmount() <= 0) {
            return null;
        }
        return Deliveries.get().keepFor(ctx, DeliveryPolicy.KEEP_SELF_CONTAINED, null, PendingDestination.giveItemNode(rest, null));
    }

    @Override
    public void take(ExecutionContext ctx, int quantity) {
        FPlayer player = ctx.getPlayer();
        InventoryDestination inventory = DeliveryDestinations.requireInventory();
        if (player == null || !inventory.take(player, matcher, quantity * item.getAmount())) {
            throw new IllegalStateException(quantity + " unit(s) of " + matcher + " could not be taken from " + (player == null ? "a context"
                    + " without a player (" + ctx.getCaller() + ")" : player.getName()) + ": fewer are held. Nothing was taken.");
        }
    }

    @Override
    public FancyText describe(ExecutionContext ctx, int quantity) {
        String name = item.getDisplayName() == null || item.getDisplayName().isEmpty() ? item.getMaterialId() : item.getDisplayName();
        return FancyText.of(quantity * item.getAmount() + "x " + name);
    }

    @Override
    public boolean canBeSold() {
        return true;
    }

    @Override
    public ItemSpec getIcon() {
        return item;
    }

    @Override
    public boolean matches(ItemSpec other) {
        return matcher.matches(other);
    }

    @Override
    public String toString() {
        return "ItemGoods{" + item.getAmount() + " x " + matcher + "}";
    }
}
