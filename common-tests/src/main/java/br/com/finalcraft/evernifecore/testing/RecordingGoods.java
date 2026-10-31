package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import br.com.finalcraft.evernifecore.menu.Goods;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Goods that forward to others and write down every give and take: {@code "give 3"}, {@code "take 1"}. */
public final class RecordingGoods implements Goods {

    private final Goods inner;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    public RecordingGoods(Goods inner) {
        this.inner = inner;
    }

    /** Every give and take, in order. */
    public List<String> getCalls() {
        return calls;
    }

    @Override
    public int count(ExecutionContext ctx) {
        return inner.count(ctx);
    }

    @Override
    public int roomFor(ExecutionContext ctx) {
        return inner.roomFor(ctx);
    }

    @Override
    public int getStackSize() {
        return inner.getStackSize();
    }

    @Override
    public @Nullable CompletableFuture<Delivery> give(ExecutionContext ctx, int quantity, @Nullable DeliveryDestination to) {
        calls.add("give " + quantity);
        return inner.give(ctx, quantity, to);
    }

    @Override
    public void take(ExecutionContext ctx, int quantity) {
        calls.add("take " + quantity);
        inner.take(ctx, quantity);
    }

    @Override
    public FancyText describe(ExecutionContext ctx, int quantity) {
        return inner.describe(ctx, quantity);
    }

    @Override
    public boolean canBeSold() {
        return inner.canBeSold();
    }

    @Override
    public @Nullable ItemSpec getIcon() {
        return inner.getIcon();
    }

    @Override
    public boolean matches(ItemSpec item) {
        return inner.matches(item);
    }
}
