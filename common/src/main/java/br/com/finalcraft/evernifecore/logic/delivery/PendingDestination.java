package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/**
 * {@value #ID}: never hands anything over now - it all becomes a pending delivery of the context's player, claimed
 * later ("collect it when you want"). Stateless, any thread.
 */
public final class PendingDestination implements DeliveryDestination {

    public static final String ID = "evernifecore:pending";

    static final PendingDestination INSTANCE = new PendingDestination();

    private PendingDestination() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public @Nullable ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item) {
        Deliveries.get().keepFor(ctx, DeliveryPolicy.KEEP_SELF_CONTAINED, null, giveItemNode(item, null));
        return null;
    }

    @Override
    public EcoResponse deliverAmount(ExecutionContext ctx, Currency currency, BigDecimal amount) {
        Deliveries.get().keepFor(ctx, DeliveryPolicy.KEEP_SELF_CONTAINED, null, moneyNode(currency, amount));
        return EcoResponse.success(amount, BigDecimal.ZERO);
    }

    /** {@code {type: evernifecore:give-item, item: <id>, amount: <n>[, data: <n>][, to: <destination>]}} - what a pending item is kept as. */
    public static JsonNode giveItemNode(ItemSpec item, @Nullable String to) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "evernifecore:give-item");
        node.put("item", item.getMaterialId());
        node.put("amount", item.getAmount());
        if (item.getData() >= 0) {
            node.put("data", item.getData());
        }
        if (to != null) {
            node.put("to", to);
        }
        return node;
    }

    /** {@code {type: evernifecore:money, amount: <n>, currency: <id>, boosted: false}}: kept already multiplied, it is not boosted twice. */
    public static JsonNode moneyNode(Currency currency, BigDecimal amount) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "evernifecore:money");
        node.put("amount", amount.toPlainString());
        node.put("currency", currency.getId());
        node.put("boosted", false);
        return node;
    }

    @Override
    public String toString() {
        return ID;
    }
}
