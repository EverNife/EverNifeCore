package br.com.finalcraft.evernifecore.math.game.options;

public class RegionGridOptions {
    
    public static final RegionGridOptions MINECRAFT_LEGACY = new RegionGridOptions(
            16,
            0,
            255
    );

    public static final RegionGridOptions MINECRAFT = new RegionGridOptions(
            16,
            -64,
            319
    );

    public static final RegionGridOptions HYTALE = new RegionGridOptions(
            32,
            0,
            320
    );
    
    private static RegionGridOptions current = MINECRAFT_LEGACY;
    
    private final int chunkSize;
    private final int chunkShift;
    private final int regionShift;
    private final int maxHeight;
    private final int minHeight;
    
    public RegionGridOptions(int chunkSize, int maxHeight, int minHeight) {
        this.chunkSize = chunkSize;
        this.chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        this.maxHeight = maxHeight;
        this.minHeight = minHeight;
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