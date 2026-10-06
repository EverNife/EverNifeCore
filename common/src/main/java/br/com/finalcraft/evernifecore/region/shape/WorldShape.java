package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;

import java.util.Collections;

/** The shape of a whole world: contains every block and is tied to no chunk. */
public final class WorldShape implements Shape {

    public static final WorldShape INSTANCE = new WorldShape();

    private WorldShape() {
    }

    @Override
    public ShapeType getType() {
        return ShapeType.WORLD;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return true;
    }

    @Override
    public BoundingBox getBounds() {
        return BoundingBox.EVERYTHING;
    }

    /** Always empty: a shape that covers the world lives in no chunk in particular. */
    @Override
    public Iterable<ChunkPos> getChunks() {
        return Collections.emptyList();
    }

    @Override
    public long getVolume() {
        return UNBOUNDED_VOLUME;
    }

    @Override
    public String toString() {
        return "WorldShape";
    }
}
