package br.com.finalcraft.evernifecore.api.common.math.vector.options;

public class RegionGridOptions {
    
    public static final RegionGridOptions MINECRAFT = new RegionGridOptions(16);
    public static final RegionGridOptions HYTALE = new RegionGridOptions(32);
    
    private static RegionGridOptions current = HYTALE;
    
    private final int chunkSize;
    private final int chunkShift;
    private final int regionShift;
    
    public RegionGridOptions(int chunkSize) {
        this.chunkSize = chunkSize;
        this.chunkShift = Integer.numberOfTrailingZeros(chunkSize);
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
}