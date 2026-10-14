package br.com.finalcraft.evernifecore.api.platoverride.block;

import org.bukkit.block.Block;

import java.util.Objects;

/**
 * A Bukkit {@link Block} handed to platform-agnostic code for the length of one event. Once the event's dispatch ends
 * the handle is closed and reading the block through it throws: keep the position, not the handle.
 */
public final class NativeBlock {

    private final Block block;
    private volatile boolean closed;

    public NativeBlock(Block block) {
        this.block = Objects.requireNonNull(block, "'block' cannot be null: a NativeBlock wraps a real block!");
    }

    /** @throws IllegalStateException once the handle is closed */
    public Block getBlock() {
        if (closed) {
            throw new IllegalStateException("The native block at " + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + ","
                    + block.getZ() + " was used after its event was decided: read what you need during the event, and keep the position,"
                    + " not the handle.");
        }
        return block;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Internal: the event's dispatch ended. */
    public void close() {
        closed = true;
    }
}
