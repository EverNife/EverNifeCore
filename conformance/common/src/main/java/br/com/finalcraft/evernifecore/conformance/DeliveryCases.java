package br.com.finalcraft.evernifecore.conformance;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryEvent;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The pending deliveries on a live server, both platforms: a {@code give-item} that fits lands in the player's native
 * inventory, and one that finds the inventory full becomes a delivery from the case's caller. Each case runs on the
 * player's thread and gives the inventory back as it found it.
 */
public final class DeliveryCases {

    /** The caller's origin, which a delivery the cases leave behind carries as its emitter. */
    public static final String EMITTER = "conformance/deliveries";
    private static final long TIMEOUT_MILLIS = 10_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private DeliveryCases() {
    }

    /** The platform's own view of the player's inventory, read and written natively on the player's thread. */
    public interface NativeInventory {

        /** How many items of the case's item the player holds. */
        int count();

        /**
         * Fills every slot an item is added to with full stacks of the case's item.
         *
         * @return what puts every slot back as it was
         */
        Runnable fill();
    }

    /** {@code give-item} of {@code amount} {@code itemId} grows the native count by exactly that and leaves nothing pending. */
    public static CompletableFuture<Verdict> inventoryFits(ECPluginData plugin, FPlayer player, String itemId, int amount,
                                                           NativeInventory inventory) throws Exception {
        int before = inventory.count();
        ExecutionContext ctx = giveItem(plugin, player, itemId, amount);
        int after = inventory.count();
        DeliveryDestinations.requireInventory().take(player, ItemMatcher.of(itemId), after - before);
        String audit = failures(ctx);
        return Deliveries.get().listOf(player.getUniqueId()).thenApply(pending -> {
            List<Delivery> mine = fromThisCase(plugin, pending);
            return Verdict.of(after - before == amount && mine.isEmpty() && audit.isEmpty(), "item=" + itemId + " before=" + before
                    + " after=" + after + " pending-from-case=" + mine.size() + audit);
        });
    }

    /**
     * {@code give-item} of one {@code itemId} into a full inventory becomes one delivery from the case's caller; the case
     * purges its plugin's deliveries after reading them.
     */
    public static CompletableFuture<Verdict> inventoryFullGoesPending(ECPluginData plugin, FPlayer player, String itemId,
                                                                      NativeInventory inventory) throws Exception {
        CompletableFuture<Delivery> enqueued = new CompletableFuture<>();
        ECEventSubscription<DeliveryEvent> watch = ECEventBus.global().subscribe(DeliveryEvent.class, event -> {
            if (event.getKind() == DeliveryEvent.Kind.ENQUEUED && event.getDelivery().getRecipient().equals(player.getUniqueId())) {
                enqueued.complete(event.getDelivery());
            }
        });
        ECScheduler.scheduleAsync(() -> enqueued.completeExceptionally(new IllegalStateException("no delivery was enqueued within "
                + TIMEOUT_MILLIS + "ms")), TIMEOUT_MILLIS);
        ExecutionContext ctx;
        int held;
        Runnable restore = inventory.fill();
        try {
            held = inventory.count();
            ctx = giveItem(plugin, player, itemId, 1);
        } finally {
            restore.run();
        }
        int fullCount = held;
        String audit = failures(ctx);
        String pluginName = plugin.getMetaInfo().getName();
        return enqueued.whenComplete((delivery, failed) -> watch.unsubscribe())
                .thenCompose(delivery -> Deliveries.get().listOf(player.getUniqueId()))
                .thenCompose(pending -> {
                    List<Delivery> mine = fromThisCase(plugin, pending);
                    return Deliveries.get().purge(pluginName, Caller.of(plugin, EMITTER)).thenApply(purged ->
                            Verdict.of(mine.size() == 1 && purged >= 1 && audit.isEmpty(), "item=" + itemId + " held-when-full=" + fullCount
                                    + " pending-from-case=" + mine + " purged=" + purged + audit));
                });
    }

    private static ExecutionContext giveItem(ECPluginData plugin, FPlayer player, String itemId, int amount) throws Exception {
        ObjectNode node = JSON.createObjectNode().put("type", "give-item").put("item", itemId).put("amount", amount);
        ExecutionContext ctx = ExecutionContext.builder(Caller.of(plugin, EMITTER)).player(player).build();
        Effects.apply(ctx, Effects.parse(plugin, node, Window.getServerZone()));
        return ctx;
    }

    private static List<Delivery> fromThisCase(ECPluginData plugin, List<Delivery> pending) {
        List<Delivery> mine = new ArrayList<>();
        for (Delivery delivery : pending) {
            if (delivery.getOrigin().getPlugin().equalsIgnoreCase(plugin.getMetaInfo().getName())
                    && delivery.getOrigin().getEmitter().equals(EMITTER)) {
                mine.add(delivery);
            }
        }
        return mine;
    }

    /** The effect failures the context audited, as a detail suffix; empty when there is none. */
    private static String failures(ExecutionContext ctx) {
        int failed = ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size();
        return failed == 0 ? "" : " effect-failed=" + ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED);
    }
}
