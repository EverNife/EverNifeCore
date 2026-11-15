package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Costs;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.EffectModifiers;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * The stock effect {@code give-item} and cost {@code item}: an item is handed to the player through a delivery
 * destination ({@code to:}, the player's inventory by default) and what does not fit goes to {@code overflow:} (a pending
 * delivery by default, the ground, or a plugin's destination); a
 * cost counts and takes matching items from the inventory and hands them back the same way on a refund.
 */
final class StockItems {

    private static final String DATA_TEXT = "The data value - the colour of a pre-1.13 wool (minecraft:wool with 14 is red), a tool's"
            + " damage; -1 for any.";

    private StockItems() {
    }

    static void register(ECPluginData core) {
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:give-item")
                .describe("Gives the player an item through to: (the inventory by default); what does not fit goes where overflow: says.")
                .param(ParamSpec.required("item", ParamSpec.Kind.ID, "The item's id, e.g. minecraft:iron_ingot."))
                .param(ParamSpec.optional("amount", ParamSpec.Kind.NUMBER, 1, "How many."))
                .param(ParamSpec.optional("data", ParamSpec.Kind.NUMBER, -1, DATA_TEXT))
                .param(ParamSpec.optional("expires", ParamSpec.Kind.DURATION, null, "How long what did not fit waits to be claimed;"
                        + " without it, until claimed."))
                .param(ParamSpec.optional("overflow", ParamSpec.Kind.ID, "pending", "Where what does not fit goes: pending (a delivery"
                        + " the player claims later), ground (dropped at the player's feet; a player who is not online gets it pending),"
                        + " or another delivery destination's id - a plugin's own decides in code, and what it hands back waits pending."))
                .factory(params -> new GiveItem(ItemSpec.of(params.getString("item"), wholeAmount(params.getNumber("amount")))
                        .withData(data(params.getNumber("data"))), params.getDuration("expires"), overflow(params.getString("overflow"))))
                .build());
        Costs.register(core, TypeDescriptor.<Cost>of("evernifecore:item")
                .describe("Takes matching items from the player's inventory; hands them back when a later cost does not come out.")
                .param(ParamSpec.required("item", ParamSpec.Kind.ID, "The item's id, e.g. minecraft:diamond; a bare name matches any namespace."))
                .param(ParamSpec.optional("amount", ParamSpec.Kind.NUMBER, 1, "How many."))
                .param(ParamSpec.optional("data", ParamSpec.Kind.NUMBER, -1, DATA_TEXT))
                .factory(params -> new ItemCost(ItemMatcher.of(params.getString("item"), data(params.getNumber("data"))),
                        wholeAmount(params.getNumber("amount"))))
                .build());
    }

    /** The destination that takes what did not fit; null for {@code pending}, which keeps it with the effect's {@code expires}. */
    private static @Nullable DeliveryDestination overflow(String id) {
        if (id.equalsIgnoreCase("pending") || id.equalsIgnoreCase(PendingDestination.ID)) {
            return null;
        }
        DeliveryDestination found = DeliveryDestinations.find(id).orElseThrow(() -> new IllegalArgumentException("No delivery destination '"
                + id + "' is registered to take what does not fit: write overflow: as one of " + DeliveryDestinations.getIds()
                + ", or have the plugin that owns it register it before this effect is read."));
        return found instanceof PendingDestination ? null : found;
    }

    private static int data(BigDecimal data) {
        if (data.compareTo(BigDecimal.ONE.negate()) < 0 || data.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("An item data value is a whole number of 0 or more (-1 for any), not " + data.toPlainString() + ".");
        }
        return data.intValueExact();
    }

    private static int wholeAmount(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("An item amount is a whole number of 1 or more, not " + amount.toPlainString() + ".");
        }
        return amount.intValueExact();
    }

    private static boolean left(@Nullable ItemSpec rest) {
        return rest != null && rest.getAmount() > 0;
    }

    private static FPlayer player(ExecutionContext ctx, String what) {
        FPlayer player = ctx.getPlayer();
        if (player == null) {
            throw new IllegalStateException(what + " reaches a player's items, and the context of " + ctx.getCaller()
                    + " has no player: build it with .player(player).");
        }
        return player;
    }

    private static final class GiveItem implements Effect {
        private final ItemSpec item;
        private final @Nullable Duration expires;
        private final @Nullable DeliveryDestination overflow;

        GiveItem(ItemSpec item, @Nullable Duration expires, @Nullable DeliveryDestination overflow) {
            this.item = item;
            this.expires = expires;
            this.overflow = overflow;
        }

        @Override
        public boolean acceptsDestination() {
            return true;
        }

        /**
         * A {@code to:} that names no registered destination keeps the whole item pending with that {@code to:}, to be
         * claimed once its plugin is back; without {@code to:} the platform's inventory must exist. What does not fit goes to
         * the {@code overflow:} destination, and what that one hands back waits pending.
         */
        @Override
        public void apply(ExecutionContext ctx) {
            String to = ctx.getAttribute(EffectModifiers.DESTINATION_ATTRIBUTE, String.class);
            DeliveryDestination destination;
            if (to == null) {
                destination = DeliveryDestinations.requireInventory();
            } else {
                Optional<DeliveryDestination> found = DeliveryDestinations.find(to);
                if (!found.isPresent()) {
                    Deliveries.get().keepOrAudit(ctx, expires, PendingDestination.giveItemNode(item, to));
                    return;
                }
                destination = found.get();
            }
            ItemSpec rest = destination.deliverItem(ctx, item);
            if (left(rest) && overflow != null) {
                rest = overflow.deliverItem(ctx, rest);
            }
            if (left(rest)) {
                Deliveries.get().keepOrAudit(ctx, expires, PendingDestination.giveItemNode(rest, null));
            }
        }

        @Override
        public String toString() {
            return "give-item(" + item.getAmount() + " " + item.getMaterialId() + ")";
        }
    }

    private static final class ItemCost implements Cost {
        private final ItemMatcher matcher;
        private final int amount;

        ItemCost(ItemMatcher matcher, int amount) {
            this.matcher = matcher;
            this.amount = amount;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            if (ctx.getPlayer() == null) {
                return ConditionResult.fail(FancyText.of("The cost " + this + " takes a player's items, and " + ctx.getCaller()
                        + " ran it without a player."));
            }
            Optional<InventoryDestination> inventory = DeliveryDestinations.inventory();
            if (!inventory.isPresent()) {
                return ConditionResult.fail(FancyText.of(DeliveryDestinations.NO_INVENTORY));
            }
            int held = inventory.get().count(ctx.getPlayer(), matcher);
            if (held >= amount) {
                return ConditionResult.pass();
            }
            return ConditionResult.fail(FancyText.of("Not enough " + matcher + ": " + held + " of " + amount + "."));
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            return DeliveryDestinations.requireInventory().take(player(ctx, "The cost " + this), matcher, amount);
        }

        /** Hands the items back through the inventory; what no longer fits waits as a pending delivery. */
        @Override
        public void refund(ExecutionContext ctx) {
            ItemSpec rest = DeliveryDestinations.requireInventory().deliverItem(ctx, matcher.toItem(amount));
            if (left(rest)) {
                Deliveries.get().keepOrAudit(ctx, null, PendingDestination.giveItemNode(rest, null));
            }
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return FancyText.of(amount + " x " + matcher);
        }

        @Override
        public String toString() {
            return "item(" + amount + " " + matcher + ")";
        }
    }
}
