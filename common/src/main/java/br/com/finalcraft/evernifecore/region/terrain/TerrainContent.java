package br.com.finalcraft.evernifecore.region.terrain;

import java.util.Objects;

/** What a journal or a snapshot keeps: the blocks, the data of their tile entities (nbt), and the non-player entities. */
public final class TerrainContent {

    /** Blocks, nbt and entities - the default. */
    public static final TerrainContent ALL = new TerrainContent(true, true, true);

    private final boolean blocks;
    private final boolean nbt;
    private final boolean entities;

    private TerrainContent(boolean blocks, boolean nbt, boolean entities) {
        this.blocks = blocks;
        this.nbt = nbt;
        this.entities = entities;
    }

    public static TerrainContent of(boolean blocks, boolean nbt, boolean entities) {
        return new TerrainContent(blocks, nbt, entities);
    }

    public boolean isBlocks() {
        return blocks;
    }

    /** The data of a tile entity (a chest's items, a sign's text); only with {@link #isBlocks()}. */
    public boolean isNbt() {
        return nbt;
    }

    public boolean isEntities() {
        return entities;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TerrainContent)) return false;
        TerrainContent that = (TerrainContent) other;
        return blocks == that.blocks && nbt == that.nbt && entities == that.entities;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocks, nbt, entities);
    }

    @Override
    public String toString() {
        return "TerrainContent{blocks=" + blocks + ", nbt=" + nbt + ", entities=" + entities + "}";
    }
}
