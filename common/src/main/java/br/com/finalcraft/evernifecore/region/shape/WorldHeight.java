package br.com.finalcraft.evernifecore.region.shape;

/** The block Y range of a world, inclusive on both ends. */
public final class WorldHeight {

    private final int minY;
    private final int maxY;

    private WorldHeight(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    /** @throws IllegalArgumentException when {@code minY} is not below {@code maxY} */
    public static WorldHeight of(int minY, int maxY) {
        if (minY >= maxY) {
            throw new IllegalArgumentException("WorldHeight got minY=" + minY + " and maxY=" + maxY
                    + ", but minY must be below maxY - check the two are not swapped (both are inclusive).");
        }
        return new WorldHeight(minY, maxY);
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorldHeight && minY == ((WorldHeight) other).minY && maxY == ((WorldHeight) other).maxY;
    }

    @Override
    public int hashCode() {
        return 31 * minY + maxY;
    }

    @Override
    public String toString() {
        return "WorldHeight[" + minY + ".." + maxY + "]";
    }
}
