package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import br.com.finalcraft.evernifecore.menu.Goods;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Goods kept as a count in memory, one count for every player: {@link #held} units held, {@link #room} that fit, a
 * stack of {@link #stackSize}. A give adds to the count (and takes room), a take removes from it. Not thread-safe.
 */
public final class CountedGoods implements Goods {

    private final String name;
    private int held;
    private int room = Integer.MAX_VALUE;
    private int stackSize = 64;
    private boolean sellable = true;

    public CountedGoods(String name, int held) {
        this.name = name;
        this.held = held;
    }

    public CountedGoods room(int room) {
        this.room = room;
        return this;
    }

    public CountedGoods stackSize(int stackSize) {
        this.stackSize = stackSize;
        return this;
    }

    public CountedGoods unsellable() {
        this.sellable = false;
        return this;
    }

    public int getHeld() {
        return held;
    }

    @Override
    public int count(ExecutionContext ctx) {
        return held;
    }

    @Override
    public int roomFor(ExecutionContext ctx) {
        return room;
    }

    @Override
    public int getStackSize() {
        return stackSize;
    }

    @Override
    public @Nullable CompletableFuture<Delivery> give(ExecutionContext ctx, int quantity, @Nullable DeliveryDestination to) {
        held += quantity;
        if (room != Integer.MAX_VALUE) {
            room = Math.max(0, room - quantity);
        }
        return null;
    }

    @Override
    public void take(ExecutionContext ctx, int quantity) {
        if (held < quantity) {
            throw new IllegalStateException(quantity + " " + name + " asked, " + held + " held.");
        }
        held -= quantity;
    }

    @Override
    public FancyText describe(ExecutionContext ctx, int quantity) {
        return FancyText.of(quantity + "x " + name);
    }

    @Override
    public boolean canBeSold() {
        return sellable;
    }

    @Override
    public String toString() {
        return "CountedGoods{" + name + ", " + held + " held}";
    }
}
