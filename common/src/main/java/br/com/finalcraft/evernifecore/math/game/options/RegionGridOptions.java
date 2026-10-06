package br.com.finalcraft.evernifecore.math.game.options;

public class RegionGridOptions {

    /** Pre-1.18 Minecraft: 16-block chunks, build height 0..255 inclusive. */
    public static final RegionGridOptions MINECRAFT_LEGACY = new RegionGridOptions(
            16,
            0,
            255
    );

    /** Minecraft 1.18+: 16-block chunks, build height -64..319 inclusive. */
    public static final RegionGridOptions MINECRAFT = new RegionGridOptions(
            16,
            -64,
            319
    );

    /** Hytale: 32-block chunks, height 0..319 inclusive ({@code ChunkUtil.MIN_Y}..{@code ChunkUtil.HEIGHT_MINUS_1}). */
    public static final RegionGridOptions HYTALE = new RegionGridOptions(
            32,
            0,
            319
    );

    private static RegionGridOptions current = MINECRAFT_LEGACY;

    private final int chunkSize;
    private final int chunkShift;
    private final int regionShift;
    private final int minHeight;
    private final int maxHeight;

    public RegionGridOptions(int chunkSize, int minHeight, int maxHeight) {
        if (minHeight >= maxHeight) {
            throw new IllegalArgumentException("RegionGridOptions got minHeight=" + minHeight + " and maxHeight=" + maxHeight
                    + ", but min must be below max. The argument order is (chunkSize, minHeight, maxHeight)"
                    + " - check the two heights are not swapped.");
        }
        this.chunkSize = chunkSize;
        this.chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.regionShift = chunkShift + 5; // 32 chunks per region
    }

    public static RegionGridOptions getCurrent() {
        return current;
    }

    public static void setCurrent(RegionGridOptions options) {
        current = options;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkShift() {
        return chunkShift;
    }

    public int getRegionShift() {
        return regionShift;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }
}
