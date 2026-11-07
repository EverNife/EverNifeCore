package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * One thing a shop trades: {@link Goods} bought or sold for a {@link Price}, behind a {@link Gate}, optionally limited,
 * confirmed and delivered elsewhere. {@code Offer.buy(Goods.ofItem(diamond), Price.of(money, 250)).confirm().build()}.
 * {@link Transactions} runs it. Immutable.
 */
public final class Offer {

    public enum Type { BUY, SELL }

    private final Type type;
    private final Goods goods;
    private final Price price;
    private final Gate gate;
    private final @Nullable String limitId;
    private final @Nullable RepeatPolicy limit;
    private final LimitScope limitScope;
    private final Quantity defaultQuantity;
    private final boolean confirmed;
    private final boolean readOnly;
    private final @Nullable DeliveryDestination destination;
    private final NoSpacePolicy noSpacePolicy;
    private final @Nullable ItemSpec icon;
    private final @Nullable Function<FPlayer, ItemSpec> iconFor;

    private Offer(Builder builder) {
        this.icon = builder.icon;
        this.iconFor = builder.iconFor;
        this.type = builder.type;
        this.goods = builder.goods;
        this.price = builder.price;
        this.gate = builder.gate;
        this.limitId = builder.limitId;
        this.limit = builder.limit;
        this.limitScope = builder.limitScope;
        this.defaultQuantity = builder.defaultQuantity;
        this.confirmed = builder.confirmed;
        this.readOnly = builder.readOnly;
        this.destination = builder.destination;
        this.noSpacePolicy = builder.noSpacePolicy;
    }

    /** The player pays {@code price} and gets {@code goods}. */
    public static Builder buy(Goods goods, Price price) {
        return new Builder(Type.BUY, goods, price);
    }

    /**
     * The player hands over {@code goods} and is paid {@code price}; every line of it must be a currency, since only a
     * currency can be paid - checked by {@link Builder#build()}.
     */
    public static Builder sell(Goods goods, Price price) {
        return new Builder(Type.SELL, goods, price);
    }

    public Type getType() {
        return type;
    }

    public Goods getGoods() {
        return goods;
    }

    public Price getPrice() {
        return price;
    }

    /** The conditions and the fixed costs on top of the price; {@link Gate#open()} by default. */
    public Gate getGate() {
        return gate;
    }

    /** The policy the trades count against; null = unlimited. */
    public @Nullable RepeatPolicy getLimit() {
        return limit;
    }

    /** The id the limit's counter is kept under, per owner; null when unlimited. */
    public @Nullable String getLimitId() {
        return limitId;
    }

    public LimitScope getLimitScope() {
        return limitScope;
    }

    /**
     * The counter this offer's limit keeps for the owner {@link #getLimitScope()} picks out of {@code ctx}; null when
     * the offer is unlimited.
     */
    public @Nullable Cooldown getCounter(ExecutionContext ctx) {
        if (limit == null || limitId == null) {
            return null;
        }
        return Cooldowns.resolve(ctx.getCaller(), limitScope.ownerOf(ctx), limitId, limit);
    }

    public Quantity getDefaultQuantity() {
        return defaultQuantity;
    }

    /** Whether a click asks {@code ScreenKey.CONFIRM} first. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** A catalog line: it quotes, and every trade on it is refused. */
    public boolean isReadOnly() {
        return readOnly;
    }

    /** Where bought goods go; null = the player's inventory, the rest as a pending delivery. */
    public @Nullable DeliveryDestination getDestination() {
        return destination;
    }

    public NoSpacePolicy getNoSpacePolicy() {
        return noSpacePolicy;
    }

    /** What the offer is drawn as for everyone: its own icon, else its goods' item; null when neither has one. */
    public @Nullable ItemSpec getIcon() {
        return icon != null ? icon : goods.getIcon();
    }

    /** What {@code viewer} sees it drawn as: the per-viewer icon's answer, else {@link #getIcon()}. */
    public @Nullable ItemSpec getIcon(@Nullable FPlayer viewer) {
        ItemSpec own = viewer == null || iconFor == null ? null : iconFor.apply(viewer);
        return own != null ? own : getIcon();
    }

    /** Whether the icon is asked per viewer ({@link Builder#icon(Function)}). */
    public boolean hasViewerIcon() {
        return iconFor != null;
    }

    @Override
    public String toString() {
        return "Offer{" + type + " " + goods + " for " + price + (limit == null ? "" : ", limit " + limitId) + "}";
    }

    public static final class Builder {
        private final Type type;
        private final Goods goods;
        private final Price price;
        private Gate gate = Gate.open();
        private String limitId;
        private RepeatPolicy limit;
        private LimitScope limitScope = LimitScope.PLAYER;
        private Quantity defaultQuantity = Quantity.ONE;
        private boolean confirmed;
        private boolean readOnly;
        private DeliveryDestination destination;
        private NoSpacePolicy noSpacePolicy = NoSpacePolicy.DELIVER_LATER;
        private ItemSpec icon;
        private Function<FPlayer, ItemSpec> iconFor;

        private Builder(Type type, Goods goods, Price price) {
            this.type = type;
            this.goods = Objects.requireNonNull(goods, "An offer trades some goods - 'goods' cannot be null (Goods.ofItem / Goods.ofEffects).");
            this.price = Objects.requireNonNull(price, "An offer has a price - 'price' cannot be null; Price.free() is the empty one.");
        }

        /** Conditions to meet and fixed costs to pay on top of the price. */
        public Builder gate(Gate gate) {
            this.gate = Objects.requireNonNull(gate, "gate");
            return this;
        }

        /**
         * At most what {@code policy} allows, counted per owner of {@code scope} under {@code counterId}: three a day per
         * player is {@code limit("diamond", RepeatPolicy.limited(3).window(Window.daily(...)), LimitScope.PLAYER)}.
         *
         * @throws IllegalArgumentException when {@code counterId} is not {@code [a-z0-9_-]{1,64}}
         */
        public Builder limit(String counterId, RepeatPolicy policy, LimitScope scope) {
            this.limitId = RegionId.requireName("offer limit", counterId);
            this.limit = Objects.requireNonNull(policy, "policy");
            this.limitScope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        public Builder defaultQuantity(Quantity quantity) {
            this.defaultQuantity = Objects.requireNonNull(quantity, "quantity");
            return this;
        }

        public Builder confirm() {
            this.confirmed = true;
            return this;
        }

        public Builder readOnly() {
            this.readOnly = true;
            return this;
        }

        public Builder to(@Nullable DeliveryDestination destination) {
            this.destination = destination;
            return this;
        }

        public Builder whenNoSpace(NoSpacePolicy policy) {
            this.noSpacePolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /** Draws the offer as {@code icon} instead of its goods' item - on its button, in a paged list and on the confirmation. */
        public Builder icon(@Nullable ItemSpec icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Draws the offer as what {@code perViewer} answers for each viewer, asked again at every redraw - a party slot drawn
         * as the Pokemon in it. A null answer draws {@link #icon(ItemSpec)}, else the goods' item.
         */
        public Builder icon(@Nullable Function<FPlayer, ItemSpec> perViewer) {
            this.iconFor = perViewer;
            return this;
        }

        /** @throws IllegalArgumentException on a SELL whose price has a line that cannot be paid */
        public Offer build() {
            if (type == Type.SELL) {
                for (PriceLine line : price.getLines()) {
                    if (!line.canPay()) {
                        throw new IllegalArgumentException("The SELL offer of " + goods + " has the price line " + line.getId() + ". A sale PAYS"
                                + " the player, and only a currency can be paid - move the item line to a BUY offer, or make it a condition"
                                + " of the gate.");
                    }
                }
            }
            return new Offer(this);
        }
    }
}
