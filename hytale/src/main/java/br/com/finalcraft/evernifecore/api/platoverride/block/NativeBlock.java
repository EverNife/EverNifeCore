package br.com.finalcraft.evernifecore.api.platoverride.block;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.Objects;

/**
 * A Hytale block - its world, position and {@link BlockType} - handed to platform-agnostic code for the length of one
 * event. Once the event's dispatch ends the handle is closed and every read throws: keep the position, not the handle.
 */
public final class NativeBlock {

    private final World world;
    private final Vector3i position;
    private final BlockType blockType;
    private volatile boolean closed;

    public NativeBlock(World world, Vector3i position, BlockType blockType) {
        this.world = Objects.requireNonNull(world, "world");
        this.position = new Vector3i(Objects.requireNonNull(position, "position"));
        this.blockType = Objects.requireNonNull(blockType, "blockType");
    }

    /** @throws IllegalStateException once the handle is closed */
    public World getWorld() {
        requireOpen();
        return world;
    }

    /** A copy of the position. @throws IllegalStateException once the handle is closed */
    public Vector3i getPosition() {
        requireOpen();
        return new Vector3i(position);
    }

    /** @throws IllegalStateException once the handle is closed */
    public BlockType getBlockType() {
        requireOpen();
        return blockType;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Internal: the event's dispatch ended. */
    public void close() {
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("The native block at " + position.x + "," + position.y + "," + position.z + " was used after its"
                    + " event was decided: read what you need during the event, and keep the position, not the handle.");
        }
    }
}
